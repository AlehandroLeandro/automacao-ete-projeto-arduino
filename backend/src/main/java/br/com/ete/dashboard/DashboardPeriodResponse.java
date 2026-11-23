package br.com.ete.dashboard;

import java.time.Instant;

public record DashboardPeriodResponse(
        Instant startAt,
        Instant endAt
) {
}
