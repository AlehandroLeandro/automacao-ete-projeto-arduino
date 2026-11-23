package br.com.ete.dashboard;

import br.com.ete.lime.LimeRecord;
import br.com.ete.lime.LimeRecordType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record DashboardLimeLogResponse(
        UUID id,
        UUID stationId,
        String stationName,
        LimeRecordType type,
        BigDecimal quantityBags,
        Instant recordedAt
) {

    public static DashboardLimeLogResponse from(LimeRecord record) {
        return new DashboardLimeLogResponse(
                record.getId(),
                record.getStation().getId(),
                record.getStation().getName(),
                record.getType(),
                record.getQuantityBags(),
                record.getRecordedAt()
        );
    }
}
