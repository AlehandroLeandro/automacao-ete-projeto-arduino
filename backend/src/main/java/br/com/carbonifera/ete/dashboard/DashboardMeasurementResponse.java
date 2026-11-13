package br.com.carbonifera.ete.dashboard;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record DashboardMeasurementResponse(
        UUID stationId,
        String stationName,
        BigDecimal value,
        String unit,
        Instant measuredAt
) {
}
