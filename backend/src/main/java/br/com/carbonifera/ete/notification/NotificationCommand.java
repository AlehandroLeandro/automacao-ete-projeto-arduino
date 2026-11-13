package br.com.carbonifera.ete.notification;

import br.com.carbonifera.ete.station.Station;
import br.com.carbonifera.ete.user.AppUser;

import java.time.Instant;
import java.util.List;

/**
 * Request to emit one {@link Notification} per resolved recipient.
 *
 * <p>{@code station} and {@code targetUrl} and {@code scheduledAt} are nullable. When
 * {@code dedupeKey} is non-null, a recipient that already has a notification for
 * {@code (targetUser, type, dedupeKey)} is skipped, providing idempotency.
 */
public record NotificationCommand(
        NotificationType type,
        List<AppUser> recipients,
        Station station,
        String title,
        String message,
        String targetUrl,
        String dedupeKey,
        Instant scheduledAt
) {
}
