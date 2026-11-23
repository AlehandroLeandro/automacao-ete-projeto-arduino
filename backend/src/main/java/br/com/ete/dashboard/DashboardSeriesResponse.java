package br.com.ete.dashboard;

import java.util.List;
import java.util.UUID;

public record DashboardSeriesResponse(
        UUID stationId,
        String stationName,
        DashboardMetric metric,
        String unit,
        List<DashboardSeriesPointResponse> points
) {
}
