package br.com.ete.dashboard;

import java.math.BigDecimal;

public record DashboardTwentyFourHourAveragesResponse(
        DashboardMeasurementAverageResponse rawPh,
        DashboardMeasurementAverageResponse treatedPh,
        DashboardMeasurementAverageResponse flow,
        BigDecimal workedHoursToday
) {
}
