package br.com.carbonifera.ete.station;

import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record LimeThresholdRequest(
        @PositiveOrZero BigDecimal minLimeStockBags
) {
}
