package br.com.ete.dashboard;

import java.util.List;

public record DashboardActivityResponse(
        List<DashboardLimeLogResponse> limeLogs,
        List<DashboardStoppageLogResponse> stoppageLogs
) {
}
