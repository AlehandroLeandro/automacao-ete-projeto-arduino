package br.com.carbonifera.ete.device;

import br.com.carbonifera.ete.audit.AuditAction;
import br.com.carbonifera.ete.audit.AuditEventRepository;
import br.com.carbonifera.ete.audit.AuditRecordType;
import br.com.carbonifera.ete.config.SchedulingConfig;
import br.com.carbonifera.ete.measurement.FlowMeasurement;
import br.com.carbonifera.ete.measurement.FlowMeasurementRepository;
import br.com.carbonifera.ete.measurement.PhMeasurement;
import br.com.carbonifera.ete.measurement.PhMeasurementRepository;
import br.com.carbonifera.ete.measurement.PhWaterType;
import br.com.carbonifera.ete.station.Station;
import br.com.carbonifera.ete.user.AppUser;
import br.com.carbonifera.ete.user.UserRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
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
    private AuditEventRepository auditEvents;
    private PlatformTransactionManager transactionManager;
    private DeviceHourlyAggregator aggregator;
    private DeviceKey key;
    private final java.util.Map<UUID, DeviceReading> pendingReadings = new java.util.HashMap<>();
    @Autowired private DeviceHourlyAggregator databaseAggregator;
    @Autowired private DeviceReadingRepository databaseReadings;
    @Autowired private DeviceKeyRepository keys;
    @Autowired private br.com.carbonifera.ete.station.StationRepository stations;
    @Autowired private br.com.carbonifera.ete.user.AppUserRepository users;
    @Autowired private FlowMeasurementRepository databaseFlows;
    @Autowired private PhMeasurementRepository databasePh;
    @Autowired private AuditEventRepository databaseAudit;
    @Autowired private PlatformTransactionManager databaseTransactions;

    @BeforeEach
    void setUp() {
        flowRepository = mock(FlowMeasurementRepository.class);
        phRepository = mock(PhMeasurementRepository.class);
        readingRepository = mock(DeviceReadingRepository.class);
        pendingReadings.clear();
        when(readingRepository.findAllById(any())).thenAnswer(invocation -> {
            List<DeviceReading> result = new ArrayList<>();
            for (UUID id : (Iterable<UUID>) invocation.getArgument(0)) {
                if (pendingReadings.containsKey(id)) result.add(pendingReadings.get(id));
            }
            return result;
        });
        auditEvents = mock(AuditEventRepository.class);
        transactionManager = mock(PlatformTransactionManager.class);
        when(flowRepository.save(any(FlowMeasurement.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(phRepository.save(any(PhMeasurement.class))).thenAnswer(invocation -> invocation.getArgument(0));
        aggregator = new DeviceHourlyAggregator(readingRepository, flowRepository, phRepository, auditEvents,
                Clock.fixed(NOW, ZoneId.of("America/Sao_Paulo")), transactionManager, mock(DeviceKeyRepository.class));
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
                new BigDecimal("6.00"), failedEnd, persistedKey, persistedKey.getName(), failedEnd, Integer.MAX_VALUE));
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

    @Test
    void mergesLatePhWithWeightedAverageAndPreservesMeasuredAt() {
        Instant measuredAt = Instant.parse("2026-11-13T17:30:00Z");
        Instant originalTime = Instant.parse("2026-11-13T18:00:00Z");
        PhMeasurement existing = PhMeasurement.fromDevice(key.getStation(), PhWaterType.RAW, new BigDecimal("7.00"),
                originalTime, key, "Bomba 1", aggregator.windowEnd(measuredAt), 60);
        when(phRepository.findByDeviceKeyAndDeviceWaterTypeAndDeviceWindowEnd(key, PhWaterType.RAW,
                aggregator.windowEnd(measuredAt))).thenReturn(java.util.Optional.of(existing));

        assertThat(aggregator.mergeLate(key, DeviceReadingType.PH_RAW, measuredAt, new BigDecimal("8.00"), null, NOW))
                .isEqualTo(DeviceHourlyAggregator.MergeOutcome.MERGED);

        assertThat(existing.getPh()).isEqualByComparingTo("7.02");
        assertThat(existing.getSampleCount()).isEqualTo(61);
        assertThat(existing.getMeasuredAt()).isEqualTo(originalTime);
        verify(phRepository).save(existing);
    }

    @Test
    void discardsEditedPhWithoutChangingIt() {
        Instant measuredAt = Instant.parse("2026-11-13T17:30:00Z");
        PhMeasurement existing = phRecord(measuredAt);
        setRecordId(existing);
        when(phRepository.findByDeviceKeyAndDeviceWaterTypeAndDeviceWindowEnd(key, PhWaterType.RAW,
                aggregator.windowEnd(measuredAt))).thenReturn(java.util.Optional.of(existing));
        when(auditEvents.existsByRecordTypeAndRecordIdAndAction(AuditRecordType.PH_MEASUREMENT,
                existing.getId(), AuditAction.UPDATED)).thenReturn(true);

        assertThat(aggregator.mergeLate(key, DeviceReadingType.PH_RAW, measuredAt, new BigDecimal("8.00"), null, NOW))
                .isEqualTo(DeviceHourlyAggregator.MergeOutcome.DISCARDED_EDITED);
        assertThat(existing.getPh()).isEqualByComparingTo("7.00");
        assertThat(existing.getSampleCount()).isEqualTo(60);
        verify(phRepository, never()).save(any(PhMeasurement.class));
    }

    @Test
    void discardsDeletedPhWithoutChangingIt() {
        Instant measuredAt = Instant.parse("2026-11-13T17:30:00Z");
        PhMeasurement existing = phRecord(measuredAt);
        existing.softDelete(null);
        when(phRepository.findByDeviceKeyAndDeviceWaterTypeAndDeviceWindowEnd(key, PhWaterType.RAW,
                aggregator.windowEnd(measuredAt))).thenReturn(java.util.Optional.of(existing));

        assertThat(aggregator.mergeLate(key, DeviceReadingType.PH_RAW, measuredAt, new BigDecimal("8.00"), null, NOW))
                .isEqualTo(DeviceHourlyAggregator.MergeOutcome.DISCARDED_DELETED);
        assertThat(existing.isDeleted()).isTrue();
        assertThat(existing.getPh()).isEqualByComparingTo("7.00");
        assertThat(existing.getSampleCount()).isEqualTo(60);
        assertThat(existing.getMeasuredAt()).isEqualTo(Instant.parse("2026-11-13T18:00:00Z"));
        verify(phRepository, never()).save(any(PhMeasurement.class));
    }

    @Test
    void discardsTooOldReadingWithoutPersistingRawOrAverage() {
        Instant measuredAt = NOW.minusSeconds(48 * 60 * 60L + 1);

        assertThat(aggregator.mergeLate(key, DeviceReadingType.PH_RAW, measuredAt, new BigDecimal("8.00"), null, NOW))
                .isEqualTo(DeviceHourlyAggregator.MergeOutcome.DISCARDED_TOO_OLD);
        verify(readingRepository, never()).save(any(DeviceReading.class));
        verify(phRepository, never()).save(any(PhMeasurement.class));
    }

    @Test
    void acceptsReadingAtFortyEightHourBoundary() {
        Instant measuredAt = NOW.minusSeconds(48 * 60 * 60L);
        when(phRepository.findByDeviceKeyAndDeviceWaterTypeAndDeviceWindowEnd(key, PhWaterType.RAW,
                aggregator.windowEnd(measuredAt))).thenReturn(java.util.Optional.empty());

        assertThat(aggregator.mergeLate(key, DeviceReadingType.PH_RAW, measuredAt, new BigDecimal("8.00"), null, NOW))
                .isEqualTo(DeviceHourlyAggregator.MergeOutcome.RAW_STORED);
        verify(readingRepository).save(any(DeviceReading.class));
    }

    @Test
    void storesLateReadingRawWhenClosedHourHasNoAverage() {
        Instant measuredAt = Instant.parse("2026-11-13T17:30:00Z");
        when(phRepository.findByDeviceKeyAndDeviceWaterTypeAndDeviceWindowEnd(key, PhWaterType.RAW,
                aggregator.windowEnd(measuredAt))).thenReturn(java.util.Optional.empty());
        when(readingRepository.save(any(DeviceReading.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(aggregator.mergeLate(key, DeviceReadingType.PH_RAW, measuredAt, new BigDecimal("8.00"), null, NOW))
                .isEqualTo(DeviceHourlyAggregator.MergeOutcome.RAW_STORED);
        org.mockito.ArgumentCaptor<DeviceReading> raw = org.mockito.ArgumentCaptor.forClass(DeviceReading.class);
        verify(readingRepository).save(raw.capture());
        assertThat(raw.getValue().getMeasuredAt()).isEqualTo(measuredAt);
        assertThat(raw.getValue().getValue()).isEqualByComparingTo("8.00");
        verify(phRepository, never()).save(any(PhMeasurement.class));
    }

    @Test
    void mergesLateFlowHeightAndLitersAsWeightedValues() {
        Instant measuredAt = Instant.parse("2026-11-13T17:30:00Z");
        FlowMeasurement existing = FlowMeasurement.fromDevice(key.getStation(), new BigDecimal("12.00"), new BigDecimal("7.00"),
                Instant.parse("2026-11-13T18:00:00Z"), key, "Bomba 1", aggregator.windowEnd(measuredAt), 60);
        when(flowRepository.findByDeviceKeyAndDeviceWindowEnd(key, aggregator.windowEnd(measuredAt)))
                .thenReturn(java.util.Optional.of(existing));

        assertThat(aggregator.mergeLate(key, DeviceReadingType.FLOW, measuredAt, new BigDecimal("8.00"),
                new BigDecimal("72.00"), NOW)).isEqualTo(DeviceHourlyAggregator.MergeOutcome.MERGED);

        assertThat(existing.getHeightCm()).isEqualByComparingTo("7.02");
        assertThat(existing.getFlowLiters()).isEqualByComparingTo("12.98");
        assertThat(existing.getSampleCount()).isEqualTo(61);
        verify(flowRepository).save(existing);
    }

    @Test
    void mergesLateTreatedPhUsingTheMatchingHourlyRecord() {
        Instant measuredAt = Instant.parse("2026-11-13T17:30:00Z");
        PhMeasurement existing = PhMeasurement.fromDevice(key.getStation(), PhWaterType.TREATED, new BigDecimal("6.00"),
                Instant.parse("2026-11-13T18:00:00Z"), key, "Bomba 1", aggregator.windowEnd(measuredAt), 2);
        when(phRepository.findByDeviceKeyAndDeviceWaterTypeAndDeviceWindowEnd(key, PhWaterType.TREATED,
                aggregator.windowEnd(measuredAt))).thenReturn(java.util.Optional.of(existing));

        assertThat(aggregator.mergeLate(key, DeviceReadingType.PH_TREATED, measuredAt, new BigDecimal("7.00"), null, NOW))
                .isEqualTo(DeviceHourlyAggregator.MergeOutcome.MERGED);
        assertThat(existing.getPh()).isEqualByComparingTo("6.33");
        assertThat(existing.getSampleCount()).isEqualTo(3);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void mergesPendingGroupIntoPersistedAverageWithOneRounding() {
        DeviceKey persistedKey = seedKey("merge-group");
        Instant at = NOW.minusSeconds(1800);
        Instant end = aggregator.windowEnd(at);
        PhMeasurement initial = databasePh.saveAndFlush(PhMeasurement.fromDevice(persistedKey.getStation(),
                PhWaterType.RAW, new BigDecimal("6.00"), end, persistedKey, "Sensor", end, 2));
        DeviceReading first = databaseReadings.saveAndFlush(new DeviceReading(persistedKey, DeviceReadingType.PH_RAW,
                new BigDecimal("7.01"), null, at));
        DeviceReading second = databaseReadings.saveAndFlush(new DeviceReading(persistedKey, DeviceReadingType.PH_RAW,
                new BigDecimal("8.00"), null, at.plusSeconds(1)));

        databaseAggregator.aggregateClosedHours(NOW);
        databaseAggregator.aggregateClosedHours(NOW);

        PhMeasurement merged = databasePh.findById(initial.getId()).orElseThrow();
        assertThat(merged.getPh()).isEqualByComparingTo("6.75");
        assertThat(merged.getSampleCount()).isEqualTo(4);
        assertThat(merged.getMeasuredAt()).isEqualTo(end);
        assertThat(databaseReadings.findById(first.getId())).isEmpty();
        assertThat(databaseReadings.findById(second.getId())).isEmpty();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void discardsPendingGroupWhenPersistedAverageWasEditedOrDeleted() {
        for (boolean deleted : List.of(false, true)) {
            DeviceKey persistedKey = seedKey("protected-" + deleted);
            Instant at = NOW.minusSeconds(1800);
            Instant end = aggregator.windowEnd(at);
            AppUser user = users.findAll().getFirst();
            PhMeasurement initial = PhMeasurement.fromDevice(persistedKey.getStation(), PhWaterType.RAW,
                    new BigDecimal("6.00"), end, persistedKey, "Sensor", end, 2);
            if (deleted) initial.softDelete(user);
            initial = databasePh.saveAndFlush(initial);
            if (!deleted) databaseAudit.saveAndFlush(new br.com.carbonifera.ete.audit.AuditEvent(
                    AuditRecordType.PH_MEASUREMENT, AuditAction.UPDATED, initial.getId(),
                    persistedKey.getStation(), user, "Human correction"));
            DeviceReading raw = databaseReadings.saveAndFlush(new DeviceReading(persistedKey, DeviceReadingType.PH_RAW,
                    new BigDecimal("8.00"), null, at));

            databaseAggregator.aggregateClosedHours(NOW);

            PhMeasurement preserved = databasePh.findById(initial.getId()).orElseThrow();
            assertThat(preserved.getPh()).isEqualByComparingTo("6.00");
            assertThat(preserved.getSampleCount()).isEqualTo(2);
            assertThat(preserved.isDeleted()).isEqualTo(deleted);
            assertThat(databaseReadings.findById(raw.getId())).isEmpty();
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void persistsLateMergeOutsideCallerTransaction() {
        DeviceKey persistedKey = seedKey("persist-late");
        Instant at = NOW.minusSeconds(1800);
        Instant end = aggregator.windowEnd(at);
        FlowMeasurement initial = databaseFlows.saveAndFlush(FlowMeasurement.fromDevice(persistedKey.getStation(),
                new BigDecimal("12.00"), new BigDecimal("7.00"), end, persistedKey, "Sensor", end, 2));

        assertThat(databaseAggregator.mergeLate(persistedKey, DeviceReadingType.FLOW, at,
                new BigDecimal("10.00"), new BigDecimal("18.00"), NOW))
                .isEqualTo(DeviceHourlyAggregator.MergeOutcome.MERGED);

        FlowMeasurement merged = databaseFlows.findById(initial.getId()).orElseThrow();
        assertThat(merged.getHeightCm()).isEqualByComparingTo("8.00");
        assertThat(merged.getFlowLiters()).isEqualByComparingTo("14.00");
        assertThat(merged.getSampleCount()).isEqualTo(3);
        assertThat(merged.getMeasuredAt()).isEqualTo(end);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void serializesConcurrentLateMergesOnTheSameHourlyAverage() throws Exception {
        DeviceKey persistedKey = seedKey("concurrent-late");
        Instant at = NOW.minusSeconds(1800);
        Instant end = aggregator.windowEnd(at);
        PhMeasurement initial = databasePh.saveAndFlush(PhMeasurement.fromDevice(persistedKey.getStation(),
                PhWaterType.RAW, new BigDecimal("6.00"), end, persistedKey, "Sensor", end, 2));
        var ready = new java.util.concurrent.CountDownLatch(2);
        var start = new java.util.concurrent.CountDownLatch(1);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<DeviceHourlyAggregator.MergeOutcome> merge = () -> {
                ready.countDown();
                if (!start.await(5, java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("start timeout");
                return databaseAggregator.mergeLate(persistedKey, DeviceReadingType.PH_RAW, at,
                        new BigDecimal("8.00"), null, NOW);
            };
            var first = executor.submit(merge);
            var second = executor.submit(merge);
            assertThat(ready.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(first.get(10, java.util.concurrent.TimeUnit.SECONDS))
                    .isEqualTo(DeviceHourlyAggregator.MergeOutcome.MERGED);
            assertThat(second.get(10, java.util.concurrent.TimeUnit.SECONDS))
                    .isEqualTo(DeviceHourlyAggregator.MergeOutcome.MERGED);
        }
        PhMeasurement merged = databasePh.findById(initial.getId()).orElseThrow();
        assertThat(merged.getPh()).isEqualByComparingTo("7.00");
        assertThat(merged.getSampleCount()).isEqualTo(4);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void waitsForHumanEditBeforeCheckingAuditAndPreservesTheCorrection() throws Exception {
        DeviceKey persistedKey = seedKey("concurrent-edit");
        Instant at = NOW.minusSeconds(1800);
        Instant end = aggregator.windowEnd(at);
        PhMeasurement initial = databasePh.saveAndFlush(PhMeasurement.fromDevice(persistedKey.getStation(),
                PhWaterType.RAW, new BigDecimal("6.00"), end, persistedKey, "Sensor", end, 2));
        AppUser user = users.findAll().getFirst();
        var started = new java.util.concurrent.CountDownLatch(1);
        var result = new java.util.concurrent.atomic.AtomicReference<
                java.util.concurrent.Future<DeviceHourlyAggregator.MergeOutcome>>();
        try (var executor = java.util.concurrent.Executors.newSingleThreadExecutor()) {
            new org.springframework.transaction.support.TransactionTemplate(databaseTransactions).executeWithoutResult(status -> {
                PhMeasurement edited = databasePh.findByIdAndDeletedFalse(initial.getId()).orElseThrow();
                edited.update(persistedKey.getStation(), PhWaterType.RAW, new BigDecimal("9.00"), end);
                databaseAudit.saveAndFlush(new br.com.carbonifera.ete.audit.AuditEvent(
                        AuditRecordType.PH_MEASUREMENT, AuditAction.UPDATED, edited.getId(),
                        persistedKey.getStation(), user, "Human correction"));
                result.set(executor.submit(() -> {
                    started.countDown();
                    return databaseAggregator.mergeLate(persistedKey, DeviceReadingType.PH_RAW, at,
                            new BigDecimal("8.00"), null, NOW);
                }));
                try {
                    assertThat(started.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
                    org.junit.jupiter.api.Assertions.assertThrows(java.util.concurrent.TimeoutException.class,
                            () -> result.get().get(200, java.util.concurrent.TimeUnit.MILLISECONDS));
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(interrupted);
                }
            });
            assertThat(result.get().get(10, java.util.concurrent.TimeUnit.SECONDS))
                    .isEqualTo(DeviceHourlyAggregator.MergeOutcome.DISCARDED_EDITED);
        }
        PhMeasurement preserved = databasePh.findById(initial.getId()).orElseThrow();
        assertThat(preserved.getPh()).isEqualByComparingTo("9.00");
        assertThat(preserved.getSampleCount()).isEqualTo(2);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void preservesRawWindowAfterHumanChangesItsTypeWithTreatedWindowPresent() {
        assertTypeEditKeepsWindowIdentity(PhWaterType.RAW, DeviceReadingType.PH_RAW,
                PhWaterType.TREATED, DeviceReadingType.PH_TREATED);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void preservesTreatedWindowAfterHumanChangesItsTypeWithRawWindowPresent() {
        assertTypeEditKeepsWindowIdentity(PhWaterType.TREATED, DeviceReadingType.PH_TREATED,
                PhWaterType.RAW, DeviceReadingType.PH_RAW);
    }

    private void assertTypeEditKeepsWindowIdentity(PhWaterType originalType, DeviceReadingType originalStream,
                                                  PhWaterType otherType, DeviceReadingType otherStream) {
        DeviceKey persistedKey = seedKey("type-edit-" + originalType);
        Instant at = NOW.minusSeconds(1800);
        Instant end = aggregator.windowEnd(at);
        PhMeasurement original = databasePh.saveAndFlush(PhMeasurement.fromDevice(persistedKey.getStation(),
                originalType, new BigDecimal("6.00"), end, persistedKey, "Sensor", end, 2));
        PhMeasurement other = databasePh.saveAndFlush(PhMeasurement.fromDevice(persistedKey.getStation(),
                otherType, new BigDecimal("7.00"), end, persistedKey, "Sensor", end, 2));
        AppUser user = users.findAll().getFirst();
        new org.springframework.transaction.support.TransactionTemplate(databaseTransactions).executeWithoutResult(status -> {
            PhMeasurement edited = databasePh.findByIdAndDeletedFalse(original.getId()).orElseThrow();
            edited.update(persistedKey.getStation(), otherType, new BigDecimal("9.00"), end.minusSeconds(60));
            databaseAudit.save(new br.com.carbonifera.ete.audit.AuditEvent(
                    AuditRecordType.PH_MEASUREMENT, AuditAction.UPDATED, edited.getId(),
                    persistedKey.getStation(), user, "Human type correction"));
        });

        assertThat(databaseAggregator.mergeLate(persistedKey, originalStream, at,
                new BigDecimal("8.00"), null, NOW))
                .isEqualTo(DeviceHourlyAggregator.MergeOutcome.DISCARDED_EDITED);
        assertThat(databaseReadings.findAll().stream()
                .filter(row -> row.getDeviceKey().getId().equals(persistedKey.getId()))).isEmpty();
        assertThat(databaseAggregator.mergeLate(persistedKey, otherStream, at,
                new BigDecimal("8.00"), null, NOW)).isEqualTo(DeviceHourlyAggregator.MergeOutcome.MERGED);
        DeviceReading discarded = databaseReadings.saveAndFlush(new DeviceReading(persistedKey, originalStream,
                new BigDecimal("8.00"), null, at));
        DeviceReading merged = databaseReadings.saveAndFlush(new DeviceReading(persistedKey, otherStream,
                new BigDecimal("9.00"), null, at));

        databaseAggregator.aggregateClosedHours(NOW);
        databaseAggregator.aggregateClosedHours(NOW);

        PhMeasurement preserved = databasePh.findById(original.getId()).orElseThrow();
        assertThat(preserved.getType()).isEqualTo(otherType);
        assertThat(preserved.getDeviceWaterType()).isEqualTo(originalType);
        assertThat(preserved.getPh()).isEqualByComparingTo("9.00");
        assertThat(preserved.getSampleCount()).isEqualTo(2);
        assertThat(preserved.getMeasuredAt()).isEqualTo(end.minusSeconds(60));
        assertThat(preserved.getDeviceWindowEnd()).isEqualTo(end);
        PhMeasurement otherWindow = databasePh.findById(other.getId()).orElseThrow();
        assertThat(otherWindow.getType()).isEqualTo(otherType);
        assertThat(otherWindow.getDeviceWaterType()).isEqualTo(otherType);
        assertThat(otherWindow.getPh()).isEqualByComparingTo("7.75");
        assertThat(otherWindow.getSampleCount()).isEqualTo(4);
        assertThat(databasePh.findAll().stream()
                .filter(row -> row.getDeviceKey().getId().equals(persistedKey.getId()))).hasSize(2);
        assertThat(databaseReadings.findById(discarded.getId())).isEmpty();
        assertThat(databaseReadings.findById(merged.getId())).isEmpty();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void aggregatesPartialHourWithRequestedNowAndWindowEnd() {
        DeviceKey persistedKey = seedKey("delete-partial");
        DeviceReading raw = databaseReadings.saveAndFlush(new DeviceReading(persistedKey,
                DeviceReadingType.PH_RAW, new BigDecimal("6.75"), null, NOW.minusSeconds(30)));

        databaseAggregator.aggregateKeyIncludingPartial(persistedKey, NOW);

        assertThat(databaseReadings.findById(raw.getId())).isEmpty();
        assertThat(databasePh.findAll().stream().filter(row -> row.getDeviceKey().getId().equals(persistedKey.getId())))
                .singleElement().satisfies(average -> {
                    assertThat(average.getMeasuredAt()).isEqualTo(NOW);
                    assertThat(average.getDeviceWindowEnd()).isEqualTo(databaseAggregator.windowEnd(raw.getMeasuredAt()));
                    assertThat(average.getPh()).isEqualByComparingTo("6.75");
                });
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void aggregatesClosedHoursForKeyWhenIncludingPartial() {
        DeviceKey persistedKey = seedKey("delete-closed");
        Instant closedAt = NOW.minusSeconds(3600);
        DeviceReading raw = databaseReadings.saveAndFlush(new DeviceReading(persistedKey,
                DeviceReadingType.PH_RAW, new BigDecimal("7.25"), null, closedAt));

        databaseAggregator.aggregateKeyIncludingPartial(persistedKey, NOW);

        assertThat(databaseReadings.findById(raw.getId())).isEmpty();
        assertThat(databasePh.findAll().stream().filter(row -> row.getDeviceKey().getId().equals(persistedKey.getId())))
                .singleElement().satisfies(average -> {
                    assertThat(average.getMeasuredAt()).isEqualTo(databaseAggregator.windowEnd(closedAt));
                    assertThat(average.getDeviceWindowEnd()).isEqualTo(databaseAggregator.windowEnd(closedAt));
                });
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void doesNotAggregateOrDeleteAnotherKeysReadings() {
        DeviceKey persistedKey = seedKey("delete-target");
        DeviceKey otherKey = seedKey("delete-other");
        DeviceReading targetRaw = databaseReadings.saveAndFlush(new DeviceReading(persistedKey,
                DeviceReadingType.PH_RAW, new BigDecimal("6.00"), null, NOW.minusSeconds(30)));
        DeviceReading otherRaw = databaseReadings.saveAndFlush(new DeviceReading(otherKey,
                DeviceReadingType.PH_RAW, new BigDecimal("8.00"), null, NOW.minusSeconds(30)));

        databaseAggregator.aggregateKeyIncludingPartial(persistedKey, NOW);

        assertThat(databaseReadings.findById(targetRaw.getId())).isEmpty();
        assertThat(databaseReadings.findById(otherRaw.getId())).isPresent();
        assertThat(databasePh.findAll().stream().filter(row -> row.getDeviceKey().getId().equals(otherKey.getId())))
                .isEmpty();
    }

    private PhMeasurement phRecord(Instant measuredAt) {
        return PhMeasurement.fromDevice(key.getStation(), PhWaterType.RAW, new BigDecimal("7.00"),
                Instant.parse("2026-11-13T18:00:00Z"), key, "Bomba 1", aggregator.windowEnd(measuredAt), 60);
    }

    private void setRecordId(Object record) {
        ReflectionTestUtils.setField(record, "id", UUID.randomUUID());
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
        pendingReadings.put(reading.getId(), reading);
        return reading;
    }

    private DeviceReading reading(DeviceReadingType type, String value, String liters, Instant measuredAt) {
        return new DeviceReading(key, type, new BigDecimal(value), liters == null ? null : new BigDecimal(liters), measuredAt);
    }
}
