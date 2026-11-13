package br.com.carbonifera.ete.lime;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record LimeRecordResponse(
        UUID id,
        UUID stationId,
        String stationName,
        LimeRecordType type,
        BigDecimal quantityBags,
        Instant recordedAt,
        String note,
        UUID createdById,
        String createdByName
) {

    public static LimeRecordResponse from(LimeRecord record) {
        return new LimeRecordResponse(
                record.getId(),
                record.getStation().getId(),
                record.getStation().getName(),
                record.getType(),
                record.getQuantityBags(),
                record.getRecordedAt(),
                record.getNote(),
                record.getCreatedBy().getId(),
                record.getCreatedBy().getName()
        );
    }
}
