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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class DeviceHourlyAggregator {

    private static final Logger log = LoggerFactory.getLogger(DeviceHourlyAggregator.class);

    private final FlowMeasurementRepository flowMeasurementRepository;
    private final PhMeasurementRepository phMeasurementRepository;
    private final Clock clock;
    private final DeviceReadingRepository deviceReadingRepository;
    private final AuditEventRepository auditEventRepository;
    private final TransactionTemplate groupTransaction;
    private final TransactionTemplate mergeTransaction;
    private final DeviceKeyRepository deviceKeyRepository;

    public DeviceHourlyAggregator(DeviceReadingRepository deviceReadingRepository,
                                  FlowMeasurementRepository flowMeasurementRepository,
                                  PhMeasurementRepository phMeasurementRepository, AuditEventRepository auditEventRepository, Clock clock,
                                  PlatformTransactionManager transactionManager, DeviceKeyRepository deviceKeyRepository) {
        this.deviceKeyRepository = deviceKeyRepository;
        this.deviceReadingRepository = deviceReadingRepository;
        this.flowMeasurementRepository = flowMeasurementRepository;
        this.phMeasurementRepository = phMeasurementRepository;
        this.auditEventRepository = auditEventRepository;
        this.clock = clock;
        this.mergeTransaction = new TransactionTemplate(transactionManager);
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
                    // ponytail: serialize all hours/types for one key; use window locks if throughput requires it.
                    deviceKeyRepository.lockById(group.deviceKey().getId());
                    List<DeviceReading> pending = deviceReadingRepository.findAllById(
                            readings.stream().map(DeviceReading::getId).toList());
                    if (pending.isEmpty()) return;
                    applyGroup(group.deviceKey(), group.type(), group.windowEnd(), pending);
                    deviceReadingRepository.deleteAllByIdIn(pending.stream().map(DeviceReading::getId).toList());
                });
            } catch (RuntimeException exception) {
                log.error("Failed to aggregate device readings for key {}, type {}, hour {}",
                        group.deviceKey().getId(), group.type(), group.windowEnd(), exception);
            }
        });
    }

    public void aggregateKeyIncludingPartial(DeviceKey key, Instant now) {
        mergeTransaction.executeWithoutResult(status -> {
            deviceKeyRepository.lockById(key.getId());
            List<DeviceReading> pending = deviceReadingRepository.findAllByDeviceKeyOrderByMeasuredAtAsc(key);
            Map<Group, List<DeviceReading>> groups = pending.stream()
                    .collect(Collectors.groupingBy(reading -> new Group(key, reading.getType(),
                            windowEnd(reading.getMeasuredAt())), LinkedHashMap::new, Collectors.toList()));
            groups.forEach((group, readings) -> {
                if (mergeExisting(key, group.type(), group.windowEnd(), readings) == null) {
                    createAverage(key, group.type(), group.windowEnd(), readings, now);
                }
            });
            if (!pending.isEmpty()) {
                deviceReadingRepository.deleteAllByIdIn(pending.stream().map(DeviceReading::getId).toList());
            }
        });
    }

    private record Group(DeviceKey deviceKey, DeviceReadingType type, Instant windowEnd) { }

    public enum MergeOutcome { MERGED, RAW_STORED, DISCARDED_EDITED, DISCARDED_DELETED, DISCARDED_TOO_OLD }

    public MergeOutcome mergeLate(DeviceKey deviceKey, DeviceReadingType type, Instant measuredAt,
                                  BigDecimal value, BigDecimal flowLiters, Instant now) {
        Instant end = windowEnd(measuredAt);
        if (now.isAfter(end.plusSeconds(24 * 60 * 60L))) {
            return MergeOutcome.DISCARDED_TOO_OLD;
        }

        return mergeTransaction.execute(status -> {
            deviceKeyRepository.lockById(deviceKey.getId());
            DeviceReading reading = new DeviceReading(deviceKey, type, value, flowLiters, measuredAt);
            MergeOutcome outcome = mergeExisting(deviceKey, type, end, List.of(reading));
            if (outcome == null) {
                deviceReadingRepository.save(reading);
                return MergeOutcome.RAW_STORED;
            }
            return outcome;
        });
    }

    private void applyGroup(DeviceKey key, DeviceReadingType type, Instant end, List<DeviceReading> readings) {
        if (mergeExisting(key, type, end, readings) == null) createAverage(key, type, end, readings);
    }

    private MergeOutcome mergeExisting(DeviceKey key, DeviceReadingType type, Instant end,
                                       List<DeviceReading> readings) {
        if (type == DeviceReadingType.FLOW) {
            Optional<FlowMeasurement> existing = flowMeasurementRepository.findByDeviceKeyAndDeviceWindowEnd(key, end);
            if (existing.isEmpty()) return null;
            FlowMeasurement measurement = existing.get();
            if (measurement.isDeleted()) return MergeOutcome.DISCARDED_DELETED;
            if (auditEventRepository.existsByRecordTypeAndRecordIdAndAction(
                    AuditRecordType.FLOW_MEASUREMENT, measurement.getId(), AuditAction.UPDATED)) {
                return MergeOutcome.DISCARDED_EDITED;
            }
            int count = measurement.getSampleCount();
            int total = Math.addExact(count, readings.size());
            measurement.mergeDeviceReading(weightedAverage(measurement.getFlowLiters(), count,
                            readings.stream().map(DeviceReading::getFlowLiters).toList()),
                    weightedAverage(measurement.getHeightCm(), count,
                            readings.stream().map(DeviceReading::getValue).toList()), total);
            flowMeasurementRepository.save(measurement);
            return MergeOutcome.MERGED;
        }

        PhWaterType waterType = type == DeviceReadingType.PH_RAW ? PhWaterType.RAW : PhWaterType.TREATED;
        Optional<PhMeasurement> existing = phMeasurementRepository.findByDeviceKeyAndDeviceWaterTypeAndDeviceWindowEnd(key, waterType, end);
        if (existing.isEmpty()) return null;
        PhMeasurement measurement = existing.get();
        if (measurement.isDeleted()) return MergeOutcome.DISCARDED_DELETED;
        if (auditEventRepository.existsByRecordTypeAndRecordIdAndAction(
                AuditRecordType.PH_MEASUREMENT, measurement.getId(), AuditAction.UPDATED)) {
            return MergeOutcome.DISCARDED_EDITED;
        }
        int count = measurement.getSampleCount();
        int total = Math.addExact(count, readings.size());
        measurement.mergeDeviceReading(weightedAverage(measurement.getPh(), count,
                readings.stream().map(DeviceReading::getValue).toList()), total);
        phMeasurementRepository.save(measurement);
        return MergeOutcome.MERGED;
    }

    private BigDecimal weightedAverage(BigDecimal average, int count, List<BigDecimal> values) {
        BigDecimal sum = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return average.multiply(BigDecimal.valueOf(count)).add(sum)
                .divide(BigDecimal.valueOf(count + (long) values.size()), 2, RoundingMode.HALF_UP);
    }

    Instant windowEnd(Instant measuredAt) {
        return ZonedDateTime.ofInstant(measuredAt, SchedulingConfig.APP_ZONE)
                .withMinute(0).withSecond(0).withNano(0).plusHours(1).toInstant();
    }

    Object createAverage(DeviceKey deviceKey, DeviceReadingType type, Instant windowEnd,
                         List<DeviceReading> readings) {
        return createAverage(deviceKey, type, windowEnd, readings, clock.instant());
    }

    private Object createAverage(DeviceKey deviceKey, DeviceReadingType type, Instant windowEnd,
                                 List<DeviceReading> readings, Instant now) {
        if (readings.isEmpty()) {
            return null;
        }
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
