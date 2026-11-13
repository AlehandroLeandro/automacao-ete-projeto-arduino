package br.com.carbonifera.ete.measurement;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class FlowRateCalculator {

    // Coefficients verified against .specs/vazao_calha_ete_m3g.xlsx.
    private static final BigDecimal A = new BigDecimal("0.1269");
    private static final BigDecimal B = new BigDecimal("0.9276");
    private static final BigDecimal C = new BigDecimal("-2.2091");
    private FlowRateCalculator() {
    }

    public static BigDecimal flowLitersFromHeightCm(BigDecimal heightCm) {
        BigDecimal flow = A.multiply(heightCm.multiply(heightCm))
                .add(B.multiply(heightCm))
                .add(C);
        return flow.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }
}
