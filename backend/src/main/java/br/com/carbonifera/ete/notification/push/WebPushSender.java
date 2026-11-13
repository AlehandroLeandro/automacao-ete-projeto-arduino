package br.com.carbonifera.ete.notification.push;

import br.com.carbonifera.ete.config.PushProperties;
import br.com.carbonifera.ete.user.AppUser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
public class WebPushSender implements PushSender {

    private static final Logger log = LoggerFactory.getLogger(WebPushSender.class);

    private final PushSubscriptionRepository repository;
    private final WebPushClient client;
    private final PushProperties properties;
    private final Clock clock;
    private final ObjectMapper mapper = new ObjectMapper();

    public WebPushSender(
            PushSubscriptionRepository repository,
            WebPushClient client,
            PushProperties properties,
            Clock clock
    ) {
        this.repository = repository;
        this.client = client;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void send(AppUser user, PushPayload payload) {
        if (!properties.hasKeys()) {
            log.debug("[push-web-noop] VAPID keys are not configured; skipping push for {}", user.getUsername());
            return;
        }

        List<PushSubscription> subscriptions = repository.findByUserAndActiveTrue(user);
        String body = serialize(payload);
        Instant sentAt = Instant.now(clock);

        for (PushSubscription subscription : subscriptions) {
            sendToSubscription(subscription, body, sentAt);
        }
    }

    private void sendToSubscription(PushSubscription subscription, String payload, Instant sentAt) {
        try {
            WebPushDeliveryResult result = client.send(subscription, payload);
            if (result.expired()) {
                subscription.deactivate();
                log.debug("[push-web-expired] endpoint={} status={}", subscription.getEndpoint(), result.statusCode());
                return;
            }
            if (!result.success()) {
                log.warn("[push-web-failed] endpoint={} status={}", subscription.getEndpoint(), result.statusCode());
                return;
            }
            subscription.markUsed(sentAt);
        } catch (Exception exception) {
            log.warn("[push-web-failed] endpoint={} error={}", subscription.getEndpoint(), exception.getMessage());
        }
    }

    private String serialize(PushPayload payload) {
        try {
            return mapper.writeValueAsString(new BrowserPushPayload(payload.title(), payload.message(), payload.url()));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize push payload", exception);
        }
    }

    private record BrowserPushPayload(String title, String body, String url) {
    }
}
