package br.com.carbonifera.ete.security;

import br.com.carbonifera.ete.config.OperatorAccessProperties;
import br.com.carbonifera.ete.user.UserRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Component
public class OperatorWindowPolicy {

    private final OperatorAccessProperties properties;
    private final Clock clock;
    private final boolean enabled;

    @Autowired
    public OperatorWindowPolicy(OperatorAccessProperties properties, Clock clock) {
        this(properties, clock, true);
    }

    private OperatorWindowPolicy(OperatorAccessProperties properties, Clock clock, boolean enabled) {
        this.properties = properties;
        this.clock = clock;
        this.enabled = enabled;
    }

    public static OperatorWindowPolicy defaultWindow(Clock clock) {
        return new OperatorWindowPolicy(new OperatorAccessProperties(24), clock);
    }

    public static OperatorWindowPolicy unrestricted() {
        return new OperatorWindowPolicy(new OperatorAccessProperties(24), Clock.systemUTC(), false);
    }

    public Optional<Instant> listLowerBound(UserPrincipal principal) {
        if (!enabled || principal == null || principal.getRole() != UserRole.OPERATOR) {
            return Optional.empty();
        }
        return Optional.of(lowerBound());
    }

    public Instant effectiveStartAt(UserPrincipal principal, Instant requestedStartAt) {
        return listLowerBound(principal)
                .filter(lowerBound -> requestedStartAt == null || lowerBound.isAfter(requestedStartAt))
                .orElse(requestedStartAt);
    }

    public void assertWithinWindow(UserPrincipal principal, Instant eventAt) {
        if (!enabled || principal == null || principal.getRole() != UserRole.OPERATOR || eventAt == null) {
            return;
        }
        if (eventAt.isBefore(lowerBound())) {
            throw new AccessDeniedException("Registro fora da janela operacional do operador");
        }
    }

    public void assertWithinWindow(UserPrincipal principal, Instant eventAt, Instant offlineQueuedAt) {
        if (offlineQueuedAt != null && !isOutsideWindow(principal, offlineQueuedAt)) return;
        assertWithinWindow(principal, eventAt);
    }

    public boolean isOutsideWindow(UserPrincipal principal, Instant eventAt) {
        return enabled
                && principal != null
                && principal.getRole() == UserRole.OPERATOR
                && eventAt != null
                && eventAt.isBefore(lowerBound());
    }

    private Instant lowerBound() {
        return Instant.now(clock).minus(Duration.ofHours(properties.recordWindowHours()));
    }
}
