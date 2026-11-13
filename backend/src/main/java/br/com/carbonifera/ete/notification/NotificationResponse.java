package br.com.carbonifera.ete.notification;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        NotificationType type,
        UUID stationId,
        String stationName,
        UUID targetUserId,
        String title,
        String message,
        String targetUrl,
        Instant scheduledAt,
        Instant sentAt,
        Instant readAt,
        String externalProvider,
        String externalStatus,
        Instant createdAt
) {

    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getType(),
                notification.getStation() == null ? null : notification.getStation().getId(),
                notification.getStation() == null ? null : notification.getStation().getName(),
                notification.getTargetUser() == null ? null : notification.getTargetUser().getId(),
                notification.getTitle(),
                notification.getMessage(),
                notification.getTargetUrl(),
                notification.getScheduledAt(),
                notification.getSentAt(),
                notification.getReadAt(),
                notification.getExternalProvider(),
                notification.getExternalStatus(),
                notification.getCreatedAt()
        );
    }
}
