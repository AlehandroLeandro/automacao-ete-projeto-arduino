package br.com.carbonifera.ete.device;

import br.com.carbonifera.ete.config.SchedulingConfig;
import br.com.carbonifera.ete.measurement.FlowMeasurement;
import br.com.carbonifera.ete.measurement.FlowMeasurementRepository;
import br.com.carbonifera.ete.measurement.PhMeasurement;
import br.com.carbonifera.ete.measurement.PhMeasurementRepository;
import br.com.carbonifera.ete.measurement.PhWaterType;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;

@Service
public class DeviceHourlyAggregator {

    private final FlowMeasurementRepository flowMeasurementRepository;
    private final PhMeasurementRepository phMeasurementRepository;
    private final Clock clock;

    public DeviceHourlyAggregator(FlowMeasurementRepository flowMeasurementRepository,
                                  PhMeasurementRepository phMeasurementRepository, Clock clock) {
        this.flowMeasurementRepository = flowMeasurementRepository;
        this.phMeasurementRepository = phMeasurementRepository;
        this.clock = clock;
    }

    Instant windowEnd(Instant measuredAt) {
        return ZonedDateTime.ofInstant(measuredAt, SchedulingConfig.APP_ZONE)
                .withMinute(0).withSecond(0).withNano(0).plusHours(1).toInstant();
    }

    Object createAverage(DeviceKey deviceKey, DeviceReadingType type, Instant windowEnd,
                         List<DeviceReading> readings) {
        if (readings.isEmpty()) {
            return null;
        }
        Instant now = clock.instant();
        Instant measuredAt = windowEnd.isBefore(now) ? windowEnd : now;
        if (type == DeviceReadingType.FLOW) {
            BigDecimal averageHeight = average(readings.stream().map(DeviceReading::getValue).toList());
            BigDecimal averageLiters = average(readings.stream().map(DeviceReading::getFlowLiters).toList());
            return flowMeasurementRepository.save(FlowMeasurement.fromDevice(deviceKey.getStation(), averageLiters,
                    averageHeight, measuredAt, deviceKey, deviceKey.getName(), windowEnd, readings.size()));
        }

        BigDecimal averagePh = average(readings.stream().map(DeviceReading::getValue).toList());
        PhWaterType waterType = type == DeviceReadingType.PH_RAW ? PhWaterType.RAW : PhWaterType.TREATED;
        return phMeasurementRepository.save(PhMeasurement.fromDevice(deviceKey.getStation(), waterType, averagePh,
                measuredAt, deviceKey, deviceKey.getName(), windowEnd, readings.size()));
    }

    private BigDecimal average(List<BigDecimal> values) {
        BigDecimal sum = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(values.size()), 2, RoundingMode.HALF_UP);
    }
}
