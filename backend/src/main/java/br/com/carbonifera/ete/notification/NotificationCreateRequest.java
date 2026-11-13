package br.com.carbonifera.ete.notification;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public record NotificationCreateRequest(
        @NotNull NotificationType type,
        UUID stationId,
        UUID targetUserId,
        @NotBlank @Size(max = 140) String title,
        @NotBlank @Size(max = 1000) String message,
        @Size(max = 300) String targetUrl,
        Instant scheduledAt
) {
}
