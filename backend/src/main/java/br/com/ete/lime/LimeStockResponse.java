package br.com.ete.lime;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record LimeStockResponse(
        UUID stationId,
        String stationName,
        BigDecimal currentStockBags,
        Instant calculatedAt
) {
}
