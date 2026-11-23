package br.com.ete.device;

import jakarta.validation.constraints.NotNull;

public record DeviceKeyActiveRequest(@NotNull Boolean active) {
}
