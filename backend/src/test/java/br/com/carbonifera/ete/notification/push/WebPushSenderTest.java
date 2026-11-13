package br.com.carbonifera.ete.notification.push;

import br.com.carbonifera.ete.config.PushProperties;
import br.com.carbonifera.ete.user.AppUser;
import br.com.carbonifera.ete.user.UserRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebPushSenderTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-13T12:00:00Z"), ZoneOffset.UTC);

    @Mock
    private PushSubscriptionRepository repository;

    @Mock
    private WebPushClient client;

    @Test
    void sendIsNoOpWhenVapidKeysAreMissing() {
        AppUser user = user();
        WebPushSender sender = sender(new PushProperties("", "", "mailto:ete@example.com"));

        sender.send(user, payload());

        verify(repository, never()).findByUserAndActiveTrue(user);
    }

    @Test
    void sendDeliversPayloadToAllActiveSubscriptions() throws Exception {
        AppUser user = user();
        PushSubscription first = subscription(user, "https://push.example/first");
        PushSubscription second = subscription(user, "https://push.example/second");
        when(repository.findByUserAndActiveTrue(user)).thenReturn(List.of(first, second));
        when(client.send(first, "{\"title\":\"Titulo\",\"body\":\"Mensagem\",\"url\":\"/app/dashboard\"}"))
                .thenReturn(WebPushDeliveryResult.success(201));
        when(client.send(second, "{\"title\":\"Titulo\",\"body\":\"Mensagem\",\"url\":\"/app/dashboard\"}"))
                .thenReturn(WebPushDeliveryResult.success(201));
        WebPushSender sender = sender(validProperties());

        sender.send(user, payload());

        assertThat(first.getLastUsedAt()).isEqualTo(CLOCK.instant());
        assertThat(second.getLastUsedAt()).isEqualTo(CLOCK.instant());
    }

    @Test
    void sendDeactivatesExpiredSubscription() throws Exception {
        AppUser user = user();
        PushSubscription expired = subscription(user, "https://push.example/expired");
        when(repository.findByUserAndActiveTrue(user)).thenReturn(List.of(expired));
        when(client.send(expired, "{\"title\":\"Titulo\",\"body\":\"Mensagem\",\"url\":\"/app/dashboard\"}"))
                .thenReturn(WebPushDeliveryResult.expired(410));
        WebPushSender sender = sender(validProperties());

        sender.send(user, payload());

        assertThat(expired.isActive()).isFalse();
    }

    @Test
    void sendContinuesAfterTransientFailure() throws Exception {
        AppUser user = user();
        PushSubscription failing = subscription(user, "https://push.example/failing");
        PushSubscription succeeding = subscription(user, "https://push.example/succeeding");
        when(repository.findByUserAndActiveTrue(user)).thenReturn(List.of(failing, succeeding));
        when(client.send(failing, "{\"title\":\"Titulo\",\"body\":\"Mensagem\",\"url\":\"/app/dashboard\"}"))
                .thenThrow(new RuntimeException("push service down"));
        when(client.send(succeeding, "{\"title\":\"Titulo\",\"body\":\"Mensagem\",\"url\":\"/app/dashboard\"}"))
                .thenReturn(WebPushDeliveryResult.success(201));
        WebPushSender sender = sender(validProperties());

        sender.send(user, payload());

        assertThat(succeeding.getLastUsedAt()).isEqualTo(CLOCK.instant());
    }

    @Test
    void sendDoesNotMarkUsedWhenProviderReturnsNonSuccessStatus() throws Exception {
        AppUser user = user();
        PushSubscription failing = subscription(user, "https://push.example/failing-status");
        when(repository.findByUserAndActiveTrue(user)).thenReturn(List.of(failing));
        when(client.send(failing, "{\"title\":\"Titulo\",\"body\":\"Mensagem\",\"url\":\"/app/dashboard\"}"))
                .thenReturn(WebPushDeliveryResult.failed(429));
        WebPushSender sender = sender(validProperties());

        sender.send(user, payload());

        assertThat(failing.getLastUsedAt()).isNull();
        assertThat(failing.isActive()).isTrue();
    }

    private WebPushSender sender(PushProperties properties) {
        return new WebPushSender(repository, client, properties, CLOCK);
    }

    private PushProperties validProperties() {
        return new PushProperties("public-key", "private-key", "mailto:ete@example.com");
    }

    private PushPayload payload() {
        return new PushPayload("Titulo", "Mensagem", "/app/dashboard");
    }

    private PushSubscription subscription(AppUser user, String endpoint) {
        return new PushSubscription(user, endpoint, "p256dh", "auth");
    }

    private AppUser user() {
        AppUser user = new AppUser("User", "user-" + UUID.randomUUID(), "hash", UserRole.OPERATOR, false);
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        return user;
    }
}
