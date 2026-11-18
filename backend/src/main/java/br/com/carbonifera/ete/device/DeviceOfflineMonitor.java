package br.com.carbonifera.ete.device;

import br.com.carbonifera.ete.config.DeviceProperties;
import br.com.carbonifera.ete.notification.NotificationCommand;
import br.com.carbonifera.ete.notification.NotificationDispatcher;
import br.com.carbonifera.ete.notification.NotificationRecipientResolver;
import br.com.carbonifera.ete.notification.NotificationType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Component
public class DeviceOfflineMonitor {
    private final DeviceKeyRepository keys;
    private final NotificationRecipientResolver recipients;
    private final NotificationDispatcher dispatcher;
    private final DeviceProperties properties;
    private final Clock clock;

    public DeviceOfflineMonitor(DeviceKeyRepository keys, NotificationRecipientResolver recipients,
                                NotificationDispatcher dispatcher, DeviceProperties properties, Clock clock) {
        this.keys = keys;
        this.recipients = recipients;
        this.dispatcher = dispatcher;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedDelay = 60000)
    @Transactional
    public void run() {
        Instant cutoff = clock.instant().minus(Duration.ofMinutes(properties.offlineMinutes()));
        for (var id : keys.findMonitorCandidateIds()) {
            keys.lockById(id).ifPresent(key -> check(key, cutoff));
        }
    }

    private void check(DeviceKey key, Instant cutoff) {
        Instant seen = key.getLastSeenAt();
        if (!key.isActive() || seen == null) return;
        Instant notified = key.getOfflineNotifiedForSeenAt();
        if (notified != null && seen.isAfter(notified)) {
            dispatch(key, NotificationType.DEVICE_BACK_ONLINE, "Dispositivo voltou a enviar dados",
                    "O dispositivo " + key.getName() + " da ETE " + key.getStation().getName() + " voltou a enviar dados.",
                    "DEVICE_ONLINE|" + key.getId() + "|" + notified);
            key.clearOfflineNotified();
            notified = null;
        }
        if (seen.isBefore(cutoff) && !seen.equals(notified)) {
            dispatch(key, NotificationType.DEVICE_OFFLINE_ALERT, "Dispositivo sem envio de dados",
                    "O dispositivo " + key.getName() + " da ETE " + key.getStation().getName() + " está sem enviar dados.",
                    "DEVICE_OFFLINE|" + key.getId() + "|" + seen);
            key.markOfflineNotified(seen);
        }
    }

    private void dispatch(DeviceKey key, NotificationType type, String title, String message, String dedupeKey) {
        dispatcher.dispatch(new NotificationCommand(type, recipients.deviceAlertRecipients(key.getStation()),
                key.getStation(), title, message, null, dedupeKey, null));
    }
}
