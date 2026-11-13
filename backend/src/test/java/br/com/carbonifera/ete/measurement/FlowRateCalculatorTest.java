package br.com.carbonifera.ete.measurement;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class FlowRateCalculatorTest {

    @Test
    void calculatesFlowFromHeight() {
        assertThat(FlowRateCalculator.flowLitersFromHeightCm(new BigDecimal("10")))
                .isEqualByComparingTo("19.76");
    }

    @Test
    void floorsZeroHeightAtZero() {
        assertThat(FlowRateCalculator.flowLitersFromHeightCm(BigDecimal.ZERO))
                .isEqualByComparingTo("0.00");
    }

    @Test
    void floorsNegativeFormulaResultAtZero() {
        assertThat(FlowRateCalculator.flowLitersFromHeightCm(BigDecimal.ONE))
                .isEqualByComparingTo("0.00");
    }

    @Test
    void acceptsMaximumHeight() {
        assertThat(FlowRateCalculator.flowLitersFromHeightCm(new BigDecimal("45")))
                .isEqualByComparingTo("296.51");
    }

    @Test
    void roundsHalfUpToTwoDecimalPlaces() {
        assertThat(FlowRateCalculator.flowLitersFromHeightCm(new BigDecimal("3")))
                .isEqualByComparingTo("1.72");
    }
}
