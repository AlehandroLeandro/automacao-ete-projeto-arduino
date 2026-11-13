package br.com.carbonifera.ete.lime;

import java.math.BigDecimal;
import java.time.Instant;

public record LimeStockEvent(
        LimeRecordType type,
        BigDecimal quantityBags,
        Instant recordedAt
) {
}
