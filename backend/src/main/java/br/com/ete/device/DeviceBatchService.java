package br.com.ete.device;

import br.com.ete.common.error.BusinessException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class DeviceBatchService {
    private final DeviceReadingService readings;
    private final DeviceBatchReceiptRepository receipts;
    private final DeviceKeyRepository keys;
    private final ObjectMapper json;
    private final Clock clock;
    DeviceBatchService(DeviceReadingService readings, DeviceBatchReceiptRepository receipts, DeviceKeyRepository keys,
                       ObjectMapper json, Clock clock) {
        this.readings = readings; this.receipts = receipts; this.keys = keys; this.json = json; this.clock = clock;
    }
    @Transactional(rollbackFor = Exception.class)
    public DeviceBatchResponse receive(UUID keyId, DeviceBatchRequest batch) throws JsonProcessingException {
        if (batch == null || batch.readings() == null || batch.readings().isEmpty() || batch.readings().size() > 100)
            throw new BusinessException("Lote invalido");
        var ids = new HashSet<String>();
        for (var sample : batch.readings()) {
            if (sample == null || sample.id() == null || !sample.id().matches("[A-Za-z0-9_-]{1,64}")
                    || !ids.add(sample.id()) || sample.timeEstimated() == null
                    || (sample.measuredAt() == null && !sample.timeEstimated())
                    || (sample.measuredAt() != null && sample.measuredAt().isAfter(clock.instant())))
                throw new BusinessException("Amostra invalida");
        }
        keys.lockById(keyId).filter(DeviceKey::isActive)
                .orElseThrow(() -> new BusinessException("Dispositivo invalido", HttpStatus.UNAUTHORIZED));
        List<DeviceBatchResponse.Result> results = new ArrayList<>();
        for (var sample : batch.readings()) {
            var receipt = receipts.findByDeviceKeyIdAndSampleId(keyId, sample.id());
            DeviceReadingResponse result;
            if (receipt.isPresent()) result = json.readValue(receipt.get().result(), DeviceReadingResponse.class);
            else {
                result = readings.process(keyId, new DeviceReadingRequest(sample.heightCm(), sample.rawPh(),
                        sample.treatedPh(), sample.measuredAt()), true, sample.timeEstimated()
                                ? (sample.measuredAt() == null ? "RECEIVED_AT" : "ELAPSED_TIME") : null);
                receipts.save(new DeviceBatchReceipt(keyId, sample.id(), json.writeValueAsString(result)));
            }
            results.add(new DeviceBatchResponse.Result(sample.id(), result.accepted(), result.rejected(), result.discarded(), receipt.isPresent()));
        }
        return new DeviceBatchResponse(List.copyOf(results));
    }
}
