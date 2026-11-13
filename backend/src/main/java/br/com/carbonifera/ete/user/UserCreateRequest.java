package br.com.carbonifera.ete.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record UserCreateRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Size(min = 3, max = 80) String username,
        @NotNull UserRole role,
        UUID stationId
) {
}
