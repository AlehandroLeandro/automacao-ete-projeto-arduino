package br.com.ete.dashboard;

import java.util.List;

public record OperationalStatusResponse(
        OperationStatus status,
        List<DashboardStoppageResponse> activeStoppages
) {
}
