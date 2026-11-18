package br.com.carbonifera.ete.device;

import br.com.carbonifera.ete.common.error.BusinessException;
import br.com.carbonifera.ete.measurement.FlowRateCalculator;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class DeviceReadingService {
    private final DeviceKeyRepository keys;
    private final DeviceReadingRepository readings;
    private final DeviceRateLimiter limiter;
    private final Clock clock;

    public DeviceReadingService(DeviceKeyRepository keys, DeviceReadingRepository readings, DeviceRateLimiter limiter, Clock clock) {
        this.keys = keys; this.readings = readings; this.limiter = limiter; this.clock = clock;
    }

    @Transactional
    public DeviceReadingResponse receive(UUID keyId, DeviceReadingRequest request) {
        if (!limiter.allow(keyId)) throw new BusinessException("Limite de requisicoes excedido", HttpStatus.TOO_MANY_REQUESTS);
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
        if (accepted.isEmpty()) throw new BusinessException("Nenhuma leitura valida");
        if (accepted.contains("heightCm")) {
            BigDecimal flow = FlowRateCalculator.flowLitersFromHeightCm(request.heightCm());
            readings.save(new DeviceReading(key, DeviceReadingType.FLOW, request.heightCm(), flow, measuredAt));
            if (key.getLastFlowAt() == null || !measuredAt.isBefore(key.getLastFlowAt())) key.recordLiveFlow(request.heightCm(), flow, measuredAt);
        }
        if (accepted.contains("rawPh")) {
            readings.save(new DeviceReading(key, DeviceReadingType.PH_RAW, request.rawPh(), null, measuredAt));
            if (key.getLastRawPhAt() == null || !measuredAt.isBefore(key.getLastRawPhAt())) key.recordLivePh(request.rawPh(), null, measuredAt);
        }
        if (accepted.contains("treatedPh")) {
            readings.save(new DeviceReading(key, DeviceReadingType.PH_TREATED, request.treatedPh(), null, measuredAt));
            if (key.getLastTreatedPhAt() == null || !measuredAt.isBefore(key.getLastTreatedPhAt())) key.recordLivePh(null, request.treatedPh(), measuredAt);
        }
        key.recordSeen(now);
        return new DeviceReadingResponse(List.copyOf(accepted), List.copyOf(rejected), List.of());
    }

    private void validate(String field, BigDecimal value, int max, List<String> accepted, List<DeviceReadingResponse.FieldResult> rejected) {
        if (value == null) return;
        if (value.signum() < 0 || value.compareTo(BigDecimal.valueOf(max)) > 0 || value.scale() > 2) {
            rejected.add(new DeviceReadingResponse.FieldResult(field, "Valor fora do intervalo ou com mais de duas casas decimais"));
        } else accepted.add(field);
    }
}
