package br.com.carbonifera.ete.measurement;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PhMeasurementUpdateRequest(
        @NotNull UUID stationId,
        @NotNull PhWaterType type,
        @NotNull @DecimalMin("0.00") @DecimalMax("14.00") @Digits(integer = 2, fraction = 2) BigDecimal ph,
        @NotNull Instant measuredAt,
        @NotBlank @Size(min = 10, max = 500) String description
) {
}
