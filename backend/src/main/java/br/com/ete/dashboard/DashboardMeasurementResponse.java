package br.com.ete.dashboard;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record DashboardMeasurementResponse(
        UUID stationId,
        String stationName,
        BigDecimal value,
        String unit,
        Instant measuredAt,
        boolean timeEstimated,
        String timeEstimateOrigin
) {
    public DashboardMeasurementResponse(UUID stationId, String stationName, BigDecimal value, String unit, Instant measuredAt) {
        this(stationId, stationName, value, unit, measuredAt, false, null);
    }
}
