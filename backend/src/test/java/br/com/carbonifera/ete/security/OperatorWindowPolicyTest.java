package br.com.carbonifera.ete.security;

import br.com.carbonifera.ete.config.OperatorAccessProperties;
import br.com.carbonifera.ete.lime.LimeService;
import br.com.carbonifera.ete.measurement.FlowMeasurementService;
import br.com.carbonifera.ete.measurement.PhMeasurementService;
import br.com.carbonifera.ete.station.Station;
import br.com.carbonifera.ete.stoppage.StoppageService;
import br.com.carbonifera.ete.user.AppUser;
import br.com.carbonifera.ete.user.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Escopo desta policy após AD-017: ela rege <b>somente</b> o domínio de paradas.
 * pH, vazão e cal passaram a ser isolados por autoria (OperatorAuthorshipPolicy) e
 * não aplicam mais piso temporal derivado da role.
 */
class OperatorWindowPolicyTest {

    private static final Instant NOW = Instant.parse("2026-07-13T15:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void listLowerBoundAppliesOnlyToOperator() {
        OperatorWindowPolicy policy = new OperatorWindowPolicy(new OperatorAccessProperties(24), CLOCK);

        assertThat(policy.listLowerBound(principal(UserRole.OPERATOR))).contains(Instant.parse("2026-07-12T15:00:00Z"));
        assertThat(policy.listLowerBound(principal(UserRole.SUPERVISOR))).isEmpty();
        assertThat(policy.listLowerBound(principal(UserRole.ADMIN))).isEmpty();
    }

    @Test
    void assertWithinWindowUsesInclusiveLowerBound() {
        OperatorWindowPolicy policy = new OperatorWindowPolicy(new OperatorAccessProperties(24), CLOCK);
        UserPrincipal operator = principal(UserRole.OPERATOR);

        policy.assertWithinWindow(operator, Instant.parse("2026-07-12T15:00:00Z"));

        assertThatThrownBy(() -> policy.assertWithinWindow(operator, Instant.parse("2026-07-12T14:59:59Z")))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void assertWithinWindowNeverRestrictsSupervisorOrAdmin() {
        OperatorWindowPolicy policy = new OperatorWindowPolicy(new OperatorAccessProperties(24), CLOCK);
        Instant longBeforeWindow = Instant.parse("2026-07-01T00:00:00Z");

        policy.assertWithinWindow(principal(UserRole.SUPERVISOR), longBeforeWindow);
        policy.assertWithinWindow(principal(UserRole.ADMIN), longBeforeWindow);

        assertThat(policy.isOutsideWindow(principal(UserRole.SUPERVISOR), longBeforeWindow)).isFalse();
        assertThat(policy.isOutsideWindow(principal(UserRole.ADMIN), longBeforeWindow)).isFalse();
        assertThat(policy.isOutsideWindow(principal(UserRole.OPERATOR), longBeforeWindow)).isTrue();
    }

    @Test
    void customWindowHoursAreApplied() {
        OperatorWindowPolicy policy = new OperatorWindowPolicy(new OperatorAccessProperties(48), CLOCK);

        assertThat(policy.listLowerBound(principal(UserRole.OPERATOR))).contains(Instant.parse("2026-07-11T15:00:00Z"));
    }

    @Test
    void recordWindowHoursIsStillBoundFromAppOperatorRecordWindowHours() {
        ConfigurationProperties binding = OperatorAccessProperties.class.getAnnotation(ConfigurationProperties.class);

        assertThat(binding).isNotNull();
        assertThat(binding.prefix()).isEqualTo("app.operator");
        assertThat(OperatorAccessProperties.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .containsExactly("recordWindowHours");
        assertThat(new OperatorAccessProperties(48).recordWindowHours()).isEqualTo(48);
    }

    @Test
    void windowPolicyGovernsStoppagesOnlyAndIsNotConsumedByCollectionServices() {
        assertThat(usesWindowPolicy(StoppageService.class)).isTrue();

        assertThat(usesWindowPolicy(FlowMeasurementService.class)).isFalse();
        assertThat(usesWindowPolicy(PhMeasurementService.class)).isFalse();
        assertThat(usesWindowPolicy(LimeService.class)).isFalse();
    }

    private boolean usesWindowPolicy(Class<?> serviceClass) {
        return Arrays.stream(serviceClass.getDeclaredFields())
                .map(Field::getType)
                .anyMatch(OperatorWindowPolicy.class::equals);
    }

    private UserPrincipal principal(UserRole role) {
        Station station = new Station("ITE A", "ite-a");
        ReflectionTestUtils.setField(station, "id", UUID.randomUUID());
        AppUser user = new AppUser("User", role.name().toLowerCase(), "hash", role, station, false);
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        return UserPrincipal.from(user);
    }
}
