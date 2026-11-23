package br.com.ete.dashboard;

import java.math.BigDecimal;
import java.time.Instant;

public record DashboardSeriesPointResponse(
        Instant measuredAt,
        BigDecimal value,
        boolean timeEstimated,
        String timeEstimateOrigin
) {
    public DashboardSeriesPointResponse(Instant measuredAt, BigDecimal value) {
        this(measuredAt, value, false, null);
    }
}
