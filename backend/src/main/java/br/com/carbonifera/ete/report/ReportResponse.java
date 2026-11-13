package br.com.carbonifera.ete.report;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ReportResponse(
        ReportType type,
        Instant startAt,
        Instant endAt,
        UUID stationId,
        boolean consolidated,
        List<ReportRowResponse> rows
) {
}
