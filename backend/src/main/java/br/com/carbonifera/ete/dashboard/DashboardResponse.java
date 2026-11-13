package br.com.carbonifera.ete.dashboard;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DashboardResponse(
        boolean consolidated,
        UUID stationId,
        Instant generatedAt,
        DashboardPeriodResponse period,
        BigDecimal limeStockBags,
        BigDecimal limeConsumptionLastShiftBags,
        DashboardCurrentDayAveragesResponse currentDayAverages,
        DashboardSeriesCollectionResponse series,
        DashboardActivityResponse activity,
        DashboardMeasurementResponse latestRawPh,
        DashboardMeasurementResponse latestTreatedPh,
        DashboardMeasurementResponse latestFlow,
        OperationalStatusResponse operation,
        List<StationDashboardResponse> stations
) {
}
