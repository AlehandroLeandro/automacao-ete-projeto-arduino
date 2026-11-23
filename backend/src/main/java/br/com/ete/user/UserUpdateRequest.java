package br.com.ete.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record UserUpdateRequest(
        @NotBlank @Size(max = 120) String name,
        @NotNull UserRole role,
        UUID stationId,
        boolean active
) {
}
