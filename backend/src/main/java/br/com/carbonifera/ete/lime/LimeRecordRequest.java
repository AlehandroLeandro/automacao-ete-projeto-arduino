package br.com.carbonifera.ete.lime;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record LimeRecordRequest(
        @NotNull UUID stationId,
        @NotNull LimeRecordType type,
        @NotNull @DecimalMin(value = "0.00", inclusive = false) @Digits(integer = 10, fraction = 2) BigDecimal quantityBags,
        @NotNull Instant recordedAt,
        @Size(max = 500) String note,
        UUID clientRequestId
) {
    public LimeRecordRequest(UUID stationId, LimeRecordType type, BigDecimal quantityBags, Instant recordedAt, String note) {
        this(stationId, type, quantityBags, recordedAt, note, null);
    }
}
