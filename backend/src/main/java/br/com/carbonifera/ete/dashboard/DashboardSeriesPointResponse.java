package br.com.carbonifera.ete.dashboard;

import java.math.BigDecimal;
import java.time.Instant;

public record DashboardSeriesPointResponse(
        Instant measuredAt,
        BigDecimal value
) {
}
