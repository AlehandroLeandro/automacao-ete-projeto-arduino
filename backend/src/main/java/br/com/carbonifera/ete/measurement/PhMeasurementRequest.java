package br.com.carbonifera.ete.measurement;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PhMeasurementRequest(
        @NotNull UUID stationId,
        @NotNull PhWaterType type,
        @NotNull @DecimalMin("0.00") @DecimalMax("14.00") @Digits(integer = 2, fraction = 2) BigDecimal ph,
        @NotNull Instant measuredAt,
        UUID clientRequestId
) {
    public PhMeasurementRequest(UUID stationId, PhWaterType type, BigDecimal ph, Instant measuredAt) {
        this(stationId, type, ph, measuredAt, null);
    }
}
