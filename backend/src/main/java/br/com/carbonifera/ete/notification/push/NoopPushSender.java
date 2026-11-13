package br.com.carbonifera.ete.notification.push;

import br.com.carbonifera.ete.user.AppUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fallback {@link PushSender} used while no real Web Push implementation (Stream D) is present.
 * It only logs at DEBUG so the in-app bell keeps working end to end without external delivery.
 */
public class NoopPushSender implements PushSender {

    private static final Logger log = LoggerFactory.getLogger(NoopPushSender.class);

    @Override
    public void send(AppUser user, PushPayload payload) {
        log.debug("[push-noop] {} -> {}", user.getUsername(), payload.title());
    }
}
