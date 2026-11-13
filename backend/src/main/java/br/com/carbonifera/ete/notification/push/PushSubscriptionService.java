package br.com.carbonifera.ete.notification.push;

import br.com.carbonifera.ete.security.CurrentUserService;
import br.com.carbonifera.ete.security.UserPrincipal;
import br.com.carbonifera.ete.user.AppUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Service
public class PushSubscriptionService {

    private final PushSubscriptionRepository repository;
    private final CurrentUserService currentUserService;
    private final Clock clock;

    public PushSubscriptionService(
            PushSubscriptionRepository repository,
            CurrentUserService currentUserService,
            Clock clock
    ) {
        this.repository = repository;
        this.currentUserService = currentUserService;
        this.clock = clock;
    }

    @Transactional
    public PushSubscription upsert(UserPrincipal principal, PushSubscriptionRequest request) {
        AppUser user = currentUserService.requireUser(principal);
        Instant now = Instant.now(clock);
        PushSubscription subscription = repository.findByEndpoint(request.endpoint())
                .orElseGet(() -> new PushSubscription(user, request.endpoint(), request.keys().p256dh(), request.keys().auth()));
        subscription.refresh(user, request.keys().p256dh(), request.keys().auth(), now);
        return repository.save(subscription);
    }

    @Transactional
    public void deactivate(UserPrincipal principal, String endpoint) {
        AppUser user = currentUserService.requireUser(principal);
        repository.findByEndpoint(endpoint)
                .filter(subscription -> subscription.getUser().getId().equals(user.getId()))
                .ifPresent(PushSubscription::deactivate);
    }
}
