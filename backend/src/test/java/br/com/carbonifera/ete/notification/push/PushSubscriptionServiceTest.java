package br.com.carbonifera.ete.notification.push;

import br.com.carbonifera.ete.security.CurrentUserService;
import br.com.carbonifera.ete.security.UserPrincipal;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PushSubscriptionServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-13T12:00:00Z"), ZoneOffset.UTC);

    @Mock
    private PushSubscriptionRepository repository;

    @Mock
    private CurrentUserService currentUserService;

    @Mock
    private UserPrincipal principal;

    @Test
    void upsertCreatesSubscriptionForCurrentUser() {
        AppUser user = user();
        when(currentUserService.requireUser(principal)).thenReturn(user);
        when(repository.findByEndpoint("https://push.example/new")).thenReturn(Optional.empty());
        when(repository.save(org.mockito.ArgumentMatchers.any(PushSubscription.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        PushSubscriptionService service = service();

        PushSubscription saved = service.upsert(principal, request("https://push.example/new", "key", "auth"));

        assertThat(saved.getUser()).isSameAs(user);
        assertThat(saved.getEndpoint()).isEqualTo("https://push.example/new");
        assertThat(saved.getP256dh()).isEqualTo("key");
        assertThat(saved.getAuth()).isEqualTo("auth");
        assertThat(saved.isActive()).isTrue();
        assertThat(saved.getLastUsedAt()).isEqualTo(CLOCK.instant());
    }

    @Test
    void upsertReactivatesExistingEndpointAndRebindsCurrentUser() {
        AppUser previousUser = user();
        AppUser currentUser = user();
        PushSubscription existing = new PushSubscription(previousUser, "https://push.example/existing", "old-key", "old-auth");
        existing.deactivate();
        when(currentUserService.requireUser(principal)).thenReturn(currentUser);
        when(repository.findByEndpoint(existing.getEndpoint())).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(existing);
        PushSubscriptionService service = service();

        PushSubscription saved = service.upsert(principal, request(existing.getEndpoint(), "new-key", "new-auth"));

        assertThat(saved.getUser()).isSameAs(currentUser);
        assertThat(saved.getP256dh()).isEqualTo("new-key");
        assertThat(saved.getAuth()).isEqualTo("new-auth");
        assertThat(saved.isActive()).isTrue();
        assertThat(saved.getLastUsedAt()).isEqualTo(CLOCK.instant());
    }

    @Test
    void deactivateIgnoresUnknownEndpoint() {
        when(repository.findByEndpoint("https://push.example/missing")).thenReturn(Optional.empty());
        when(currentUserService.requireUser(principal)).thenReturn(user());
        PushSubscriptionService service = service();

        service.deactivate(principal, "https://push.example/missing");

        verify(repository).findByEndpoint("https://push.example/missing");
    }

    @Test
    void deactivateMarksKnownEndpointInactive() {
        AppUser user = user();
        PushSubscription existing = new PushSubscription(user, "https://push.example/known", "key", "auth");
        when(currentUserService.requireUser(principal)).thenReturn(user);
        when(repository.findByEndpoint(existing.getEndpoint())).thenReturn(Optional.of(existing));
        PushSubscriptionService service = service();

        service.deactivate(principal, existing.getEndpoint());

        assertThat(existing.isActive()).isFalse();
    }

    @Test
    void deactivateDoesNotAffectSubscriptionFromAnotherUser() {
        AppUser currentUser = user();
        AppUser owner = user();
        PushSubscription existing = new PushSubscription(owner, "https://push.example/owned", "key", "auth");
        when(currentUserService.requireUser(principal)).thenReturn(currentUser);
        when(repository.findByEndpoint(existing.getEndpoint())).thenReturn(Optional.of(existing));
        PushSubscriptionService service = service();

        service.deactivate(principal, existing.getEndpoint());

        assertThat(existing.isActive()).isTrue();
        verify(repository, never()).deleteByEndpoint(existing.getEndpoint());
    }

    private PushSubscriptionService service() {
        return new PushSubscriptionService(repository, currentUserService, CLOCK);
    }

    private PushSubscriptionRequest request(String endpoint, String p256dh, String auth) {
        return new PushSubscriptionRequest(endpoint, new PushSubscriptionRequest.Keys(p256dh, auth));
    }

    private AppUser user() {
        AppUser user = new AppUser("User", "user-" + UUID.randomUUID(), "hash", UserRole.OPERATOR, false);
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        return user;
    }
}
