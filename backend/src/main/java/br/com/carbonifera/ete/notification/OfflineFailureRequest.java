package br.com.carbonifera.ete.notification;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public record OfflineFailureRequest(
        @NotNull UUID clientRequestId,
        @NotBlank @Size(max = 30) String domain,
        UUID stationId,
        @NotBlank @Size(max = 30) String operation,
        @NotBlank @Size(max = 1000) String reason,
        @NotNull Instant occurredAt
) {}
