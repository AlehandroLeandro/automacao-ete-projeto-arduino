package br.com.carbonifera.ete.dashboard;

import br.com.carbonifera.ete.stoppage.Stoppage;
import br.com.carbonifera.ete.stoppage.StoppageReason;

import java.time.Instant;
import java.util.UUID;

public record DashboardStoppageResponse(
        UUID stationId,
        String stationName,
        StoppageReason reason,
        String reasonDetail,
        Instant startAt,
        long elapsedMinutes
) {

    public static DashboardStoppageResponse from(Stoppage stoppage, Instant reference) {
        return new DashboardStoppageResponse(
                stoppage.getStation().getId(),
                stoppage.getStation().getName(),
                stoppage.getReason(),
                stoppage.getReasonDetail(),
                stoppage.getStartAt(),
                stoppage.durationUntil(reference).toMinutes()
        );
    }
}
