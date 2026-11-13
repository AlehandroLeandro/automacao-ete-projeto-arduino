package br.com.carbonifera.ete.lime;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record LimeRecordListResponse(
        UUID id,
        UUID stationId,
        String stationName,
        LimeRecordType type,
        BigDecimal quantityBags,
        BigDecimal stockAfterBags,
        Instant recordedAt,
        String note,
        UUID createdById,
        String createdByName
) {

    public static LimeRecordListResponse from(LimeRecord record, BigDecimal stockAfterBags) {
        return new LimeRecordListResponse(
                record.getId(),
                record.getStation().getId(),
                record.getStation().getName(),
                record.getType(),
                record.getQuantityBags(),
                stockAfterBags,
                record.getRecordedAt(),
                record.getNote(),
                record.getCreatedBy().getId(),
                record.getCreatedBy().getName()
        );
    }
}
