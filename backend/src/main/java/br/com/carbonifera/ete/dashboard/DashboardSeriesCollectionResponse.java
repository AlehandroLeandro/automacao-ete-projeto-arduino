package br.com.carbonifera.ete.dashboard;

import java.util.List;

public record DashboardSeriesCollectionResponse(
        List<DashboardSeriesResponse> flow,
        List<DashboardSeriesResponse> rawPh,
        List<DashboardSeriesResponse> treatedPh,
        List<DashboardSeriesResponse> lime
) {
}
