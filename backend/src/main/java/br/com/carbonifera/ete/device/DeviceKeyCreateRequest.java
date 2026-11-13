package br.com.carbonifera.ete.device;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record DeviceKeyCreateRequest(@NotBlank String name, @NotNull UUID stationId) {
}
