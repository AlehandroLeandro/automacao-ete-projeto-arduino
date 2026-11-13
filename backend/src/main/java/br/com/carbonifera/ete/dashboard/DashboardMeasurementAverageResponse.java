package br.com.carbonifera.ete.dashboard;

import java.math.BigDecimal;
import java.time.Instant;

public record DashboardMeasurementAverageResponse(
        BigDecimal value,
        String unit,
        long sampleCount,
        Instant startAt,
        Instant endAt
) {
}
