package br.com.ete.device;

import br.com.ete.common.error.BusinessException;
import br.com.ete.measurement.FlowRateCalculator;
import br.com.ete.config.SchedulingConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class DeviceReadingService {
    private static final Logger log = LoggerFactory.getLogger(DeviceReadingService.class);
    private final DeviceHourlyAggregator aggregator;
    private final DeviceKeyRepository keys;
    private final DeviceReadingRepository readings;
    private final DeviceRateLimiter limiter;
    private final Clock clock;

    public DeviceReadingService(DeviceKeyRepository keys, DeviceReadingRepository readings, DeviceRateLimiter limiter, Clock clock, DeviceHourlyAggregator aggregator) {
        this.keys = keys; this.readings = readings; this.limiter = limiter; this.clock = clock; this.aggregator = aggregator;
    }

    @Transactional
    public DeviceReadingResponse receive(UUID keyId, DeviceReadingRequest request) {
        if (!limiter.allow(keyId)) throw new BusinessException("Limite de requisicoes excedido", HttpStatus.TOO_MANY_REQUESTS);
        return process(keyId, request, false, null);
    }

    DeviceReadingResponse process(UUID keyId, DeviceReadingRequest request, boolean allowEmpty, String origin) {
        DeviceKey key = keys.lockById(keyId).filter(DeviceKey::isActive)
                .orElseThrow(() -> new BusinessException("Dispositivo invalido", HttpStatus.UNAUTHORIZED));
        Instant now = clock.instant();
        Instant measuredAt = request.measuredAt() == null ? now : request.measuredAt();
        if (measuredAt.isAfter(now)) throw new BusinessException("Data de medicao no futuro");
        List<String> accepted = new ArrayList<>();
        List<DeviceReadingResponse.FieldResult> rejected = new ArrayList<>();
        validate("heightCm", request.heightCm(), 45, accepted, rejected);
        validate("rawPh", request.rawPh(), 14, accepted, rejected);
        validate("treatedPh", request.treatedPh(), 14, accepted, rejected);
        if (accepted.isEmpty() && !allowEmpty) throw new BusinessException("Nenhuma leitura valida");
        List<DeviceReadingResponse.FieldResult> discarded = new ArrayList<>();
        if (accepted.contains("heightCm")) {
            BigDecimal flow = FlowRateCalculator.flowLitersFromHeightCm(request.heightCm());
            if (store(key, DeviceReadingType.FLOW, "heightCm", request.heightCm(), flow, measuredAt, now, discarded, origin)
                    && (key.getLastFlowAt() == null || !measuredAt.isBefore(key.getLastFlowAt()))) { key.recordLiveFlow(request.heightCm(), flow, measuredAt); key.setLastFlowTimeEstimateOrigin(origin); }
        }
        if (accepted.contains("rawPh")) {
            if (store(key, DeviceReadingType.PH_RAW, "rawPh", request.rawPh(), null, measuredAt, now, discarded, origin)
                    && (key.getLastRawPhAt() == null || !measuredAt.isBefore(key.getLastRawPhAt()))) { key.recordLivePh(request.rawPh(), null, measuredAt); key.setLastRawPhTimeEstimateOrigin(origin); }
        }
        if (accepted.contains("treatedPh")) {
            if (store(key, DeviceReadingType.PH_TREATED, "treatedPh", request.treatedPh(), null, measuredAt, now, discarded, origin)
                    && (key.getLastTreatedPhAt() == null || !measuredAt.isBefore(key.getLastTreatedPhAt()))) { key.recordLivePh(null, request.treatedPh(), measuredAt); key.setLastTreatedPhTimeEstimateOrigin(origin); }
        }
        discarded.forEach(result -> accepted.remove(result.field()));
        key.recordSeen(now);
        return new DeviceReadingResponse(List.copyOf(accepted), List.copyOf(rejected), List.copyOf(discarded));
    }

    private boolean store(DeviceKey key, DeviceReadingType type, String field, BigDecimal value, BigDecimal flow,
                       Instant measuredAt, Instant now, List<DeviceReadingResponse.FieldResult> discarded, String origin) {
        Instant currentHour = ZonedDateTime.ofInstant(now, SchedulingConfig.APP_ZONE)
                .withMinute(0).withSecond(0).withNano(0).toInstant();
        if (!measuredAt.isBefore(currentHour)) {
            DeviceReading reading = new DeviceReading(key, type, value, flow, measuredAt);
            reading.setTimeEstimateOrigin(origin);
            readings.save(reading);
            return true;
        }
        DeviceHourlyAggregator.MergeOutcome outcome = origin == null
                ? aggregator.mergeLate(key, type, measuredAt, value, flow, now)
                : aggregator.mergeLate(key, type, measuredAt, value, flow, now, origin);
        String reason = switch (outcome) {
            case DISCARDED_EDITED -> "EDITED";
            case DISCARDED_DELETED -> "DELETED";
            case DISCARDED_TOO_OLD -> "TOO_OLD";
            default -> null;
        };
        if (reason != null) {
            discarded.add(new DeviceReadingResponse.FieldResult(field, reason));
            log.info("Discarded late device reading: key={}, field={}, reason={}", key.getId(), field, reason);
            return false;
        }
        return true;
    }

    private void validate(String field, BigDecimal value, int max, List<String> accepted, List<DeviceReadingResponse.FieldResult> rejected) {
        if (value == null) return;
        if (value.signum() < 0 || value.compareTo(BigDecimal.valueOf(max)) > 0 || value.scale() > 2) {
            rejected.add(new DeviceReadingResponse.FieldResult(field, "Valor fora do intervalo ou com mais de duas casas decimais"));
        } else accepted.add(field);
    }
}
