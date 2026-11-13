package br.com.carbonifera.ete.stoppage;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

public record StoppageResponse(
        UUID id,
        UUID stationId,
        String stationName,
        StoppageReason reason,
        String reasonDetail,
        StoppageStatus status,
        Instant startAt,
        Instant endAt,
        long durationMinutes,
        UUID createdById,
        String createdByName
) {

    public static StoppageResponse from(Stoppage stoppage, Instant reference) {
        Duration duration = stoppage.durationUntil(reference);
        return new StoppageResponse(
                stoppage.getId(),
                stoppage.getStation().getId(),
                stoppage.getStation().getName(),
                stoppage.getReason(),
                stoppage.getReasonDetail(),
                stoppage.getStatus(),
                stoppage.getStartAt(),
                stoppage.getEndAt(),
                duration.toMinutes(),
                stoppage.getCreatedBy().getId(),
                stoppage.getCreatedBy().getName()
        );
    }
}
