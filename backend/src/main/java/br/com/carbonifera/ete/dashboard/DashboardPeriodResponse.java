package br.com.carbonifera.ete.dashboard;

import java.time.Instant;

public record DashboardPeriodResponse(
        Instant startAt,
        Instant endAt
) {
}
