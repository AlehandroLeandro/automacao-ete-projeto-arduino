package br.com.carbonifera.ete.device;

import br.com.carbonifera.ete.measurement.FlowMeasurement;
import br.com.carbonifera.ete.measurement.FlowMeasurementRepository;
import br.com.carbonifera.ete.measurement.PhMeasurement;
import br.com.carbonifera.ete.measurement.PhMeasurementRepository;
import br.com.carbonifera.ete.measurement.PhWaterType;
import br.com.carbonifera.ete.station.Station;
import br.com.carbonifera.ete.user.AppUser;
import br.com.carbonifera.ete.user.UserRole;
import br.com.carbonifera.ete.config.SchedulingConfig;
import br.com.carbonifera.ete.measurement.PhWaterType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({DeviceHourlyAggregator.class, SchedulingConfig.class})
class DeviceHourlyAggregatorTest {

    private static final Instant NOW = Instant.parse("2026-11-13T18:00:30Z");
    private FlowMeasurementRepository flowRepository;
    private PhMeasurementRepository phRepository;
    private DeviceReadingRepository readingRepository;
    private PlatformTransactionManager transactionManager;
    private DeviceHourlyAggregator aggregator;
    private DeviceKey key;
    @Autowired private DeviceHourlyAggregator databaseAggregator;
    @Autowired private DeviceReadingRepository databaseReadings;
    @Autowired private DeviceKeyRepository keys;
    @Autowired private br.com.carbonifera.ete.station.StationRepository stations;
    @Autowired private br.com.carbonifera.ete.user.AppUserRepository users;
    @Autowired private FlowMeasurementRepository databaseFlows;
    @Autowired private PhMeasurementRepository databasePh;

    @BeforeEach
    void setUp() {
        flowRepository = mock(FlowMeasurementRepository.class);
        phRepository = mock(PhMeasurementRepository.class);
        readingRepository = mock(DeviceReadingRepository.class);
        transactionManager = mock(PlatformTransactionManager.class);
        when(flowRepository.save(any(FlowMeasurement.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(phRepository.save(any(PhMeasurement.class))).thenAnswer(invocation -> invocation.getArgument(0));
        aggregator = new DeviceHourlyAggregator(readingRepository, flowRepository, phRepository,
                Clock.fixed(NOW, ZoneId.of("America/Sao_Paulo")), transactionManager);
        Station station = new Station("Station", "station");
        key = mock(DeviceKey.class);
        when(key.getName()).thenReturn("Bomba 1");
        when(key.getStation()).thenReturn(station);
    }

    @Test
    void readingAtLastSecondEndsAtNextLocalHour() {
        assertThat(aggregator.windowEnd(Instant.parse("2026-11-13T17:59:59Z")))
                .isEqualTo(Instant.parse("2026-11-13T18:00:00Z"));
    }

    @Test
    void readingAtHourBoundaryEndsAtFollowingHour() {
        assertThat(aggregator.windowEnd(Instant.parse("2026-11-13T18:00:00Z")))
                .isEqualTo(Instant.parse("2026-11-13T19:00:00Z"));
    }

    @Test
    void createsPhAverageWithHalfUpAndAllHourlyMetadata() {
        List<DeviceReading> readings = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            readings.add(reading(DeviceReadingType.PH_RAW, i < 30 ? "6.00" : "6.01", null,
                    Instant.parse("2026-11-13T17:00:00Z").plusSeconds(i * 60L)));
        }
        Instant end = Instant.parse("2026-11-13T18:00:00Z");

        PhMeasurement result = (PhMeasurement) aggregator.createAverage(key, DeviceReadingType.PH_RAW, end, readings);

        assertThat(result.getPh()).isEqualByComparingTo("6.01");
        assertThat(result.getType()).isEqualTo(PhWaterType.RAW);
        assertThat(result.getSampleCount()).isEqualTo(60);
        assertThat(result.getMeasuredAt()).isEqualTo(end);
        assertThat(result.getDeviceWindowEnd()).isEqualTo(end);
        assertThat(result.getDeviceKey()).isSameAs(key);
        assertThat(result.getDeviceName()).isEqualTo("Bomba 1");
        assertThat(result.getCreatedBy()).isNull();
    }

    @Test
    void createsTreatedPhForOutputReadings() {
        PhMeasurement result = (PhMeasurement) aggregator.createAverage(key, DeviceReadingType.PH_TREATED,
                Instant.parse("2026-11-13T18:00:00Z"), List.of(reading(DeviceReadingType.PH_TREATED, "7.25", null,
                        Instant.parse("2026-11-13T17:30:00Z"))));

        assertThat(result.getType()).isEqualTo(PhWaterType.TREATED);
        assertThat(result.getPh()).isEqualByComparingTo("7.25");
    }

    @Test
    void averagesHeightAndTheRecordedLitersSeparately() {
        FlowMeasurement result = (FlowMeasurement) aggregator.createAverage(key, DeviceReadingType.FLOW,
                Instant.parse("2026-11-13T18:00:00Z"), List.of(
                        reading(DeviceReadingType.FLOW, "10.00", "9.00", Instant.parse("2026-11-13T17:10:00Z")),
                        reading(DeviceReadingType.FLOW, "20.01", "40.00", Instant.parse("2026-11-13T17:20:00Z"))));

        assertThat(result.getHeightCm()).isEqualByComparingTo("15.01");
        assertThat(result.getFlowLiters()).isEqualByComparingTo("24.50");
        assertThat(result.getSampleCount()).isEqualTo(2);
        assertThat(result.getCreatedBy()).isNull();
        assertThat(result.getDeviceName()).isEqualTo("Bomba 1");
    }

    @Test
    void capsMeasurementTimeAtNowWhenTheWindowHasNotEnded() {
        Instant end = Instant.parse("2026-11-13T19:00:00Z");

        PhMeasurement result = (PhMeasurement) aggregator.createAverage(key, DeviceReadingType.PH_RAW, end,
                List.of(reading(DeviceReadingType.PH_RAW, "6.50", null, Instant.parse("2026-11-13T18:15:00Z"))));

        assertThat(result.getMeasuredAt()).isEqualTo(NOW);
    }

    @Test
    void aggregatesClosedGroupAndDeletesItsRawReadings() {
        DeviceReading raw = persistedReading(DeviceReadingType.PH_RAW, "6.50", "2026-11-13T17:30:00Z");
        when(readingRepository.findPendingBefore(Instant.parse("2026-11-13T18:00:00Z"))).thenReturn(List.of(raw));

        aggregator.aggregateClosedHours(NOW);

        verify(phRepository).save(any(PhMeasurement.class));
        verify(readingRepository).deleteAllByIdIn(List.of(raw.getId()));
    }

    @Test
    void leavesCurrentHourUntouched() {
        when(readingRepository.findPendingBefore(Instant.parse("2026-11-13T18:00:00Z"))).thenReturn(List.of());

        aggregator.aggregateClosedHours(NOW);

        verify(flowRepository, never()).save(any(FlowMeasurement.class));
        verify(phRepository, never()).save(any(PhMeasurement.class));
        verify(readingRepository, never()).deleteAllByIdIn(anyCollection());
    }

    @Test
    void catchesUpAllPendingHoursInOneRun() {
        DeviceReading earlier = persistedReading(DeviceReadingType.PH_RAW, "6.00", "2026-11-13T15:20:00Z");
        DeviceReading later = persistedReading(DeviceReadingType.PH_RAW, "7.00", "2026-11-13T17:20:00Z");
        when(readingRepository.findPendingBefore(Instant.parse("2026-11-13T18:00:00Z")))
                .thenReturn(List.of(earlier, later));

        aggregator.aggregateClosedHours(NOW);

        verify(phRepository, times(2)).save(any(PhMeasurement.class));
        verify(readingRepository, times(2)).deleteAllByIdIn(anyCollection());
    }

    @Test
    void emptyQueryCreatesNoAverage() {
        when(readingRepository.findPendingBefore(Instant.parse("2026-11-13T18:00:00Z"))).thenReturn(List.of());

        aggregator.aggregateClosedHours(NOW);

        verify(flowRepository, never()).save(any(FlowMeasurement.class));
        verify(phRepository, never()).save(any(PhMeasurement.class));
    }

    @Test
    void secondRunDoesNotDuplicateAfterRawReadingsWereRemoved() {
        DeviceReading raw = persistedReading(DeviceReadingType.PH_RAW, "6.50", "2026-11-13T17:30:00Z");
        when(readingRepository.findPendingBefore(Instant.parse("2026-11-13T18:00:00Z")))
                .thenReturn(List.of(raw), List.of());

        aggregator.aggregateClosedHours(NOW);
        aggregator.aggregateClosedHours(NOW);

        verify(phRepository, times(1)).save(any(PhMeasurement.class));
    }

    @Test
    void continuesAfterOneGroupFails() {
        DeviceReading failing = persistedReading(DeviceReadingType.PH_RAW, "6.00", "2026-11-13T15:20:00Z");
        DeviceReading succeeding = persistedReading(DeviceReadingType.PH_RAW, "7.00", "2026-11-13T17:20:00Z");
        when(readingRepository.findPendingBefore(Instant.parse("2026-11-13T18:00:00Z")))
                .thenReturn(List.of(failing, succeeding));
        when(phRepository.save(any(PhMeasurement.class))).thenThrow(new IllegalStateException("database failure"))
                .thenAnswer(invocation -> invocation.getArgument(0));

        aggregator.aggregateClosedHours(NOW);

        verify(readingRepository, times(1)).deleteAllByIdIn(anyCollection());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void persistsAverageAndDeletesRawRowsInDatabase() {
        DeviceKey persistedKey = seedKey("aggregate");
        Instant now = Instant.now().plusSeconds(20).truncatedTo(java.time.temporal.ChronoUnit.HOURS);
        Instant closedReadingAt = now.minusSeconds(1800);
        DeviceReading raw = databaseReadings.saveAndFlush(new DeviceReading(persistedKey, DeviceReadingType.PH_RAW,
                new BigDecimal("6.80"), null, closedReadingAt));

        databaseAggregator.aggregateClosedHours(now);

        assertThat(databaseReadings.findById(raw.getId())).isEmpty();
        assertThat(databasePh.findAll()).anySatisfy(average -> {
            assertThat(average.getPh()).isEqualByComparingTo("6.80");
            assertThat(average.getSampleCount()).isEqualTo(1);
            assertThat(average.getDeviceKey().getId()).isEqualTo(persistedKey.getId());
        });
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void leavesOpenHourRawRowsInDatabase() {
        DeviceKey persistedKey = seedKey("open");
        Instant now = Instant.now().plusSeconds(20).truncatedTo(java.time.temporal.ChronoUnit.HOURS);
        DeviceReading raw = databaseReadings.saveAndFlush(new DeviceReading(persistedKey, DeviceReadingType.PH_RAW,
                new BigDecimal("6.80"), null, now.plusSeconds(60)));

        databaseAggregator.aggregateClosedHours(now.plusSeconds(120));

        assertThat(databaseReadings.findById(raw.getId())).isPresent();
        assertThat(databasePh.findAll().stream().filter(row -> row.getDeviceKey().getId().equals(persistedKey.getId())))
                .isEmpty();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void catchesUpMultipleHoursAndRepeatedRunsStayIdempotent() {
        DeviceKey persistedKey = seedKey("catch-up");
        Instant now = Instant.now().plusSeconds(20).truncatedTo(java.time.temporal.ChronoUnit.HOURS);
        databaseReadings.saveAndFlush(new DeviceReading(persistedKey, DeviceReadingType.PH_RAW,
                new BigDecimal("6.00"), null, now.minusSeconds(9000)));
        databaseReadings.saveAndFlush(new DeviceReading(persistedKey, DeviceReadingType.PH_RAW,
                new BigDecimal("7.00"), null, now.minusSeconds(1800)));

        databaseAggregator.aggregateClosedHours(now);
        databaseAggregator.aggregateClosedHours(now);

        assertThat(databaseReadings.findAll().stream().filter(row -> row.getDeviceKey().getId().equals(persistedKey.getId())))
                .isEmpty();
        assertThat(databasePh.findAll().stream().filter(row -> row.getDeviceKey().getId().equals(persistedKey.getId())))
                .hasSize(2);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void rollsBackFailedGroupAndCommitsOtherGroups() {
        DeviceKey persistedKey = seedKey("isolated-failure");
        Instant now = Instant.now().plusSeconds(20).truncatedTo(java.time.temporal.ChronoUnit.HOURS);
        Instant failedAt = now.minusSeconds(9000);
        Instant succeededAt = now.minusSeconds(1800);
        Instant failedEnd = aggregator.windowEnd(failedAt);
        databasePh.saveAndFlush(PhMeasurement.fromDevice(persistedKey.getStation(), PhWaterType.RAW,
                new BigDecimal("6.00"), failedEnd, persistedKey, persistedKey.getName(), failedEnd, 1));
        DeviceReading failedRaw = databaseReadings.saveAndFlush(new DeviceReading(persistedKey,
                DeviceReadingType.PH_RAW, new BigDecimal("6.50"), null, failedAt));
        DeviceReading succeededRaw = databaseReadings.saveAndFlush(new DeviceReading(persistedKey,
                DeviceReadingType.PH_RAW, new BigDecimal("7.00"), null, succeededAt));

        databaseAggregator.aggregateClosedHours(now);

        assertThat(databaseReadings.findById(failedRaw.getId())).isPresent();
        assertThat(databaseReadings.findById(succeededRaw.getId())).isEmpty();
        assertThat(databasePh.findAll().stream().filter(row -> row.getDeviceKey().getId().equals(persistedKey.getId())))
                .hasSize(2);
    }

    private DeviceKey seedKey(String suffix) {
        Station station = stations.saveAndFlush(new Station("Hourly " + suffix, "hourly-" + suffix));
        AppUser admin = users.saveAndFlush(new AppUser("Admin", "hourly-" + suffix + "-" + UUID.randomUUID(),
                "password", UserRole.ADMIN, false));
        return keys.saveAndFlush(new DeviceKey(station, "Sensor", "encrypted", UUID.randomUUID().toString(), admin));
    }

    private DeviceReading persistedReading(DeviceReadingType type, String value, String at) {
        DeviceReading reading = reading(type, value, null, Instant.parse(at));
        ReflectionTestUtils.setField(reading, "id", UUID.randomUUID());
        return reading;
    }

    private DeviceReading reading(DeviceReadingType type, String value, String liters, Instant measuredAt) {
        return new DeviceReading(key, type, new BigDecimal(value), liters == null ? null : new BigDecimal(liters), measuredAt);
    }
}
