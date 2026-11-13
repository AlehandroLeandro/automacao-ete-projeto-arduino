package br.com.carbonifera.ete.user;

import java.util.UUID;

public record UserResponse(
        UUID id,
        String name,
        String username,
        UserRole role,
        UUID stationId,
        String stationName,
        boolean changePassword,
        boolean active
) {

    public static UserResponse from(AppUser user) {
        return new UserResponse(
                user.getId(),
                user.getName(),
                user.getUsername(),
                user.getRole(),
                user.getStation() == null ? null : user.getStation().getId(),
                user.getStation() == null ? null : user.getStation().getName(),
                user.isChangePassword(),
                user.isActive()
        );
    }
}
