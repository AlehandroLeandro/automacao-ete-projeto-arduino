package br.com.carbonifera.ete.notification;

import br.com.carbonifera.ete.notification.push.PushPayload;
import br.com.carbonifera.ete.notification.push.PushSender;
import br.com.carbonifera.ete.user.AppUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
@Transactional
public class NotificationDispatcherImpl implements NotificationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatcherImpl.class);

    private final NotificationRepository notificationRepository;
    private final PushSender pushSender;

    public NotificationDispatcherImpl(NotificationRepository notificationRepository, PushSender pushSender) {
        this.notificationRepository = notificationRepository;
        this.pushSender = pushSender;
    }

    @Override
    public List<Notification> dispatch(NotificationCommand command) {
        List<Notification> created = new ArrayList<>();
        for (AppUser recipient : command.recipients()) {
            if (isDuplicate(command, recipient)) {
                continue;
            }
            created.add(persistAndPush(command, recipient));
        }
        return created;
    }

    private boolean isDuplicate(NotificationCommand command, AppUser recipient) {
        return command.dedupeKey() != null
                && notificationRepository.existsByTargetUserAndTypeAndDedupeKey(
                        recipient, command.type(), command.dedupeKey());
    }

    private Notification persistAndPush(NotificationCommand command, AppUser recipient) {
        Notification notification = new Notification(
                command.type(),
                command.station(),
                recipient,
                command.title(),
                command.message(),
                command.targetUrl(),
                command.scheduledAt(),
                command.dedupeKey()
        );
        notification.markSent(Instant.now());
        Notification saved = notificationRepository.save(notification);
        sendPush(command, recipient);
        return saved;
    }

    private void sendPush(NotificationCommand command, AppUser recipient) {
        try {
            pushSender.send(recipient, new PushPayload(command.title(), command.message(), command.targetUrl()));
        } catch (RuntimeException ex) {
            log.warn("[push] failed to send notification to {}: {}", recipient.getUsername(), ex.getMessage());
        }
    }
}
