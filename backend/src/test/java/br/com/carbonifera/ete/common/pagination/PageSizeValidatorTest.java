package br.com.carbonifera.ete.common.pagination;

import br.com.carbonifera.ete.common.error.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PageSizeValidatorTest {
    @Test
    void acceptsSupportedSizesAndRejectsOthers() {
        for (int size : new int[]{10, 50, 100}) {
            assertThatCode(() -> PageSizeValidator.validate(size)).doesNotThrowAnyException();
        }
        assertThatThrownBy(() -> PageSizeValidator.validate(20))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Page size must be one of: 10, 50, 100");
    }
}
