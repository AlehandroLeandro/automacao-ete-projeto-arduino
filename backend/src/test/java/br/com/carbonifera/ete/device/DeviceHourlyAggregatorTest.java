package br.com.carbonifera.ete.device;

import br.com.carbonifera.ete.measurement.FlowMeasurement;
import br.com.carbonifera.ete.measurement.FlowMeasurementRepository;
import br.com.carbonifera.ete.measurement.PhMeasurement;
import br.com.carbonifera.ete.measurement.PhMeasurementRepository;
import br.com.carbonifera.ete.measurement.PhWaterType;
import br.com.carbonifera.ete.station.Station;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DeviceHourlyAggregatorTest {

    private static final Instant NOW = Instant.parse("2026-11-13T18:00:30Z");
    private FlowMeasurementRepository flowRepository;
    private PhMeasurementRepository phRepository;
    private DeviceHourlyAggregator aggregator;
    private DeviceKey key;

    @BeforeEach
    void setUp() {
        flowRepository = mock(FlowMeasurementRepository.class);
        phRepository = mock(PhMeasurementRepository.class);
        when(flowRepository.save(any(FlowMeasurement.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(phRepository.save(any(PhMeasurement.class))).thenAnswer(invocation -> invocation.getArgument(0));
        aggregator = new DeviceHourlyAggregator(flowRepository, phRepository,
                Clock.fixed(NOW, ZoneId.of("America/Sao_Paulo")));
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

    private DeviceReading reading(DeviceReadingType type, String value, String liters, Instant measuredAt) {
        return new DeviceReading(key, type, new BigDecimal(value), liters == null ? null : new BigDecimal(liters), measuredAt);
    }
}
