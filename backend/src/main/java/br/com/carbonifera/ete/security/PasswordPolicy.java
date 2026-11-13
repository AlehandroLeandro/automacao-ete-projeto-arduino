package br.com.carbonifera.ete.security;

import br.com.carbonifera.ete.common.error.BusinessException;

public final class PasswordPolicy {

    private PasswordPolicy() {
    }

    public static void validatePermanentPassword(String password, String confirmation) {
        if (!password.equals(confirmation)) {
            throw new BusinessException("Password confirmation does not match");
        }
        if (password.length() < 8) {
            throw new BusinessException("Password must have at least 8 characters");
        }
        if (password.chars().anyMatch(Character::isWhitespace)) {
            throw new BusinessException("Password must not contain whitespace");
        }
    }
}
