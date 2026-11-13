package br.com.carbonifera.ete.measurement;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record FlowMeasurementRequest(
        @NotNull UUID stationId,
        @NotNull @DecimalMin("0") @DecimalMax("45") @Digits(integer = 2, fraction = 2) BigDecimal heightCm,
        @NotNull Instant measuredAt,
        UUID clientRequestId
) {
    public FlowMeasurementRequest(UUID stationId, BigDecimal heightCm, Instant measuredAt) {
        this(stationId, heightCm, measuredAt, null);
    }
}
