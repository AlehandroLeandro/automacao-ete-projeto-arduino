package br.com.carbonifera.ete.device;

import br.com.carbonifera.ete.config.SchedulingConfig;
import br.com.carbonifera.ete.measurement.FlowMeasurement;
import br.com.carbonifera.ete.measurement.FlowMeasurementRepository;
import br.com.carbonifera.ete.measurement.PhMeasurement;
import br.com.carbonifera.ete.measurement.PhMeasurementRepository;
import br.com.carbonifera.ete.measurement.PhWaterType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class DeviceHourlyAggregator {

    private static final Logger log = LoggerFactory.getLogger(DeviceHourlyAggregator.class);

    private final FlowMeasurementRepository flowMeasurementRepository;
    private final PhMeasurementRepository phMeasurementRepository;
    private final Clock clock;
    private final DeviceReadingRepository deviceReadingRepository;
    private final TransactionTemplate groupTransaction;

    public DeviceHourlyAggregator(DeviceReadingRepository deviceReadingRepository,
                                  FlowMeasurementRepository flowMeasurementRepository,
                                  PhMeasurementRepository phMeasurementRepository, Clock clock,
                                  PlatformTransactionManager transactionManager) {
        this.deviceReadingRepository = deviceReadingRepository;
        this.flowMeasurementRepository = flowMeasurementRepository;
        this.phMeasurementRepository = phMeasurementRepository;
        this.clock = clock;
        this.groupTransaction = new TransactionTemplate(transactionManager);
        this.groupTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public void aggregateClosedHours(Instant now) {
        Instant currentHourStart = ZonedDateTime.ofInstant(now, SchedulingConfig.APP_ZONE)
                .withMinute(0).withSecond(0).withNano(0).toInstant();
        Map<Group, List<DeviceReading>> groups = deviceReadingRepository.findPendingBefore(currentHourStart).stream()
                .collect(Collectors.groupingBy(reading -> new Group(reading.getDeviceKey(), reading.getType(),
                        windowEnd(reading.getMeasuredAt())), LinkedHashMap::new, Collectors.toList()));

        groups.forEach((group, readings) -> {
            try {
                groupTransaction.executeWithoutResult(status -> {
                    createAverage(group.deviceKey(), group.type(), group.windowEnd(), readings);
                    deviceReadingRepository.deleteAllByIdIn(readings.stream().map(DeviceReading::getId).toList());
                });
            } catch (RuntimeException exception) {
                log.error("Failed to aggregate device readings for key {}, type {}, hour {}",
                        group.deviceKey().getId(), group.type(), group.windowEnd(), exception);
            }
        });
    }

    private record Group(DeviceKey deviceKey, DeviceReadingType type, Instant windowEnd) { }

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
