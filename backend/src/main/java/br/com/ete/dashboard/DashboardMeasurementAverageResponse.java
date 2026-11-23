package br.com.ete.dashboard;

import java.math.BigDecimal;
import java.time.Instant;

public record DashboardMeasurementAverageResponse(
        BigDecimal value,
        String unit,
        long sampleCount,
        Instant startAt,
        Instant endAt,
        boolean timeEstimated,
        String timeEstimateOrigin
) {
    public DashboardMeasurementAverageResponse(BigDecimal value, String unit, long sampleCount, Instant startAt, Instant endAt) {
        this(value, unit, sampleCount, startAt, endAt, false, null);
    }
}
