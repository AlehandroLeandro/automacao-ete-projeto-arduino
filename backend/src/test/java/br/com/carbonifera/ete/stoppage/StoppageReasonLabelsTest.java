package br.com.carbonifera.ete.stoppage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class StoppageReasonLabelsTest {

    @ParameterizedTest
    @EnumSource(StoppageReason.class)
    void mapsEveryStoppageReasonToPortugueseLabel(StoppageReason reason) {
        assertThat(StoppageReasonLabels.of(reason))
                .isNotBlank()
                .isNotEqualTo(reason.name());
    }

    @Test
    void labelsPumpClogging() {
        assertThat(StoppageReasonLabels.of(StoppageReason.ENTUPIMENTO_DE_BOMBAS)).isEqualTo("Entupimento de bombas");
    }

    @Test
    void describeAppendsFreeTextWhenPresent() {
        assertThat(StoppageReasonLabels.describe(StoppageReason.OUTROS, "Queda de energia"))
                .isEqualTo("Outros - Queda de energia");
    }

    @ParameterizedTest
    @EnumSource(StoppageReason.class)
    void describeFallsBackToPlainLabelWithoutFreeText(StoppageReason reason) {
        assertThat(StoppageReasonLabels.describe(reason, null)).isEqualTo(StoppageReasonLabels.of(reason));
        assertThat(StoppageReasonLabels.describe(reason, "  ")).isEqualTo(StoppageReasonLabels.of(reason));
    }
}
