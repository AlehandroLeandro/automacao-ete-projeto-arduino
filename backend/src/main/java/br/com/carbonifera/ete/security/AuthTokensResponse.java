package br.com.carbonifera.ete.security;

import br.com.carbonifera.ete.user.UserRole;

import java.util.UUID;

public record AuthTokensResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        UUID userId,
        String name,
        String username,
        UserRole role,
        UUID stationId,
        String stationSlug,
        String stationName,
        boolean changePassword,
        long operatorRecordWindowHours
) {
}
