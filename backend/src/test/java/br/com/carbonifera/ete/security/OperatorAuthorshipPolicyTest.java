package br.com.carbonifera.ete.security;

import br.com.carbonifera.ete.common.error.ResourceNotFoundException;
import br.com.carbonifera.ete.station.Station;
import br.com.carbonifera.ete.user.AppUser;
import br.com.carbonifera.ete.user.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OperatorAuthorshipPolicyTest {

    private static final String NOT_FOUND_MESSAGE = "Flow measurement not found";

    private final OperatorAuthorshipPolicy policy = new OperatorAuthorshipPolicy();
    private final Station station = station();

    @Test
    void authorFilterReturnsOwnIdForOperator() {
        AppUser operator = user(UserRole.OPERATOR);
        UserPrincipal principal = UserPrincipal.from(operator);

        assertThat(policy.authorFilter(principal)).contains(operator.getId());
        assertThat(policy.authorFilterOrNull(principal)).isEqualTo(operator.getId());
    }

    @Test
    void authorFilterIsEmptyForSupervisorAndAdmin() {
        UserPrincipal supervisor = UserPrincipal.from(user(UserRole.SUPERVISOR));
        UserPrincipal admin = UserPrincipal.from(user(UserRole.ADMIN));

        assertThat(policy.authorFilter(supervisor)).isEmpty();
        assertThat(policy.authorFilter(admin)).isEmpty();
        assertThat(policy.authorFilterOrNull(supervisor)).isNull();
        assertThat(policy.authorFilterOrNull(admin)).isNull();
    }

    @Test
    void authorFilterIsEmptyWhenPrincipalIsNull() {
        assertThat(policy.authorFilter(null)).isEmpty();
        assertThat(policy.authorFilterOrNull(null)).isNull();
    }

    @Test
    void assertOwnedPassesForOperatorOwnRecord() {
        AppUser operator = user(UserRole.OPERATOR);

        assertThatCode(() -> policy.assertOwned(UserPrincipal.from(operator), operator, NOT_FOUND_MESSAGE))
                .doesNotThrowAnyException();
    }

    @Test
    void assertOwnedThrowsNotFoundWithDomainMessageForOperatorForeignRecord() {
        AppUser operator = user(UserRole.OPERATOR);
        AppUser otherOperator = user(UserRole.OPERATOR);

        assertThatThrownBy(() -> policy.assertOwned(UserPrincipal.from(operator), otherOperator, NOT_FOUND_MESSAGE))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage(NOT_FOUND_MESSAGE);
    }

    @Test
    void assertOwnedIgnoresAuthorForSupervisorAndAdmin() {
        AppUser supervisor = user(UserRole.SUPERVISOR);
        AppUser admin = user(UserRole.ADMIN);
        AppUser someOperator = user(UserRole.OPERATOR);

        assertThatCode(() -> policy.assertOwned(UserPrincipal.from(supervisor), someOperator, NOT_FOUND_MESSAGE))
                .doesNotThrowAnyException();
        assertThatCode(() -> policy.assertOwned(UserPrincipal.from(admin), someOperator, NOT_FOUND_MESSAGE))
                .doesNotThrowAnyException();
    }

    private Station station() {
        Station created = new Station("ITE A", "ite-a");
        ReflectionTestUtils.setField(created, "id", UUID.randomUUID());
        return created;
    }

    private AppUser user(UserRole role) {
        AppUser appUser = new AppUser("User", role.name().toLowerCase() + UUID.randomUUID(), "hash", role, station, false);
        ReflectionTestUtils.setField(appUser, "id", UUID.randomUUID());
        return appUser;
    }
}
