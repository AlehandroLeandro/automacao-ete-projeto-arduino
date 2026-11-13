package br.com.carbonifera.ete.common.pagination;

import br.com.carbonifera.ete.common.error.BusinessException;

public final class PageSizeValidator {
    private static final String INVALID_SIZE_MESSAGE = "Page size must be one of: 10, 50, 100";

    private PageSizeValidator() {
    }

    public static void validate(int size) {
        if (size != 10 && size != 50 && size != 100) throw new BusinessException(INVALID_SIZE_MESSAGE);
    }
}
