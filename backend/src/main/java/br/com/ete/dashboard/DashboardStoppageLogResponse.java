package br.com.ete.dashboard;

import br.com.ete.stoppage.Stoppage;
import br.com.ete.stoppage.StoppageReason;
import br.com.ete.stoppage.StoppageStatus;

import java.time.Instant;
import java.util.UUID;

public record DashboardStoppageLogResponse(
        UUID id,
        UUID stationId,
        String stationName,
        StoppageReason reason,
        String reasonDetail,
        StoppageStatus status,
        Instant startAt,
        Instant endAt,
        long durationMinutes
) {

    public static DashboardStoppageLogResponse from(Stoppage stoppage, Instant reference) {
        return new DashboardStoppageLogResponse(
                stoppage.getId(),
                stoppage.getStation().getId(),
                stoppage.getStation().getName(),
                stoppage.getReason(),
                stoppage.getReasonDetail(),
                stoppage.getStatus(),
                stoppage.getStartAt(),
                stoppage.getEndAt(),
                stoppage.durationUntil(reference).toMinutes()
        );
    }
}
