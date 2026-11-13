package br.com.carbonifera.ete.dashboard;

import java.math.BigDecimal;
import java.time.Instant;

public record DashboardCurrentDayAveragesResponse(
        Instant startAt,
        Instant endAt,
        DashboardMeasurementAverageResponse rawPh,
        DashboardMeasurementAverageResponse treatedPh,
        DashboardMeasurementAverageResponse flow,
        BigDecimal workedHoursToday,
        BigDecimal flowAverageToday,
        BigDecimal flowTotalToday,
        BigDecimal flowAveragePerWorkedHour
) {
}
