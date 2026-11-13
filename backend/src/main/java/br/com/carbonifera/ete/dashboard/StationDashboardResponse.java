package br.com.carbonifera.ete.dashboard;

import java.math.BigDecimal;
import java.util.UUID;

public record StationDashboardResponse(
        UUID stationId,
        String stationName,
        BigDecimal limeStockBags,
        BigDecimal limeConsumptionLastShiftBags,
        DashboardMeasurementResponse latestRawPh,
        DashboardMeasurementResponse latestTreatedPh,
        DashboardMeasurementResponse latestFlow,
        OperationalStatusResponse operation,
        BigDecimal workedHoursToday
) {
}
