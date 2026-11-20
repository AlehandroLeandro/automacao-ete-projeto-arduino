package br.com.carbonifera.ete.device;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record DeviceBatchRequest(List<Sample> readings) {
    public record Sample(String id, BigDecimal heightCm, BigDecimal rawPh, BigDecimal treatedPh,
                         Instant measuredAt, Boolean timeEstimated) { }
}
