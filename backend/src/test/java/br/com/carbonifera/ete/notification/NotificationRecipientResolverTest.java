package br.com.carbonifera.ete.notification;

import br.com.carbonifera.ete.station.Station;
import br.com.carbonifera.ete.user.AppUser;
import br.com.carbonifera.ete.user.AppUserRepository;
import br.com.carbonifera.ete.user.UserRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationRecipientResolverTest {

    @Mock
    private AppUserRepository userRepository;

    @InjectMocks
    private NotificationRecipientResolver resolver;

    @Test
    void activeSupervisorsQueriesRepositoryForActiveSupervisors() {
        AppUser supervisor = user(UserRole.SUPERVISOR);
        when(userRepository.findAllByRoleAndActiveTrue(UserRole.SUPERVISOR)).thenReturn(List.of(supervisor));

        List<AppUser> result = resolver.activeSupervisors();

        assertThat(result).containsExactly(supervisor);
        verify(userRepository).findAllByRoleAndActiveTrue(UserRole.SUPERVISOR);
    }

    @Test
    void stoppageAlertRecipientsIncludeSupervisorsAndStationViewers() {
        Station station = mock(Station.class);
        AppUser supervisor = user(UserRole.SUPERVISOR);
        AppUser viewer = user(UserRole.VIEWER);
        when(userRepository.findAllByRoleAndActiveTrue(UserRole.SUPERVISOR)).thenReturn(List.of(supervisor));
        when(userRepository.findAllByRoleAndStationAndActiveTrue(UserRole.VIEWER, station)).thenReturn(List.of(viewer));

        List<AppUser> result = resolver.stoppageAlertRecipients(station);

        assertThat(result).containsExactly(supervisor, viewer);
    }

    @Test
    void activeOperatorsQueriesRepositoryForActiveOperators() {
        AppUser operator = user(UserRole.OPERATOR);
        when(userRepository.findAllByRoleAndActiveTrue(UserRole.OPERATOR)).thenReturn(List.of(operator));

        List<AppUser> result = resolver.activeOperators();

        assertThat(result).containsExactly(operator);
        verify(userRepository).findAllByRoleAndActiveTrue(UserRole.OPERATOR);
    }

    @Test
    void deviceNotificationTypesAreAvailable() {
        assertThat(NotificationType.values()).contains(
                NotificationType.DEVICE_OFFLINE_ALERT,
                NotificationType.DEVICE_BACK_ONLINE);
    }

    @Test
    void deviceAlertRecipientsIncludeActiveAdminsSupervisorsAndStationOperatorsOnce() {
        Station station = mock(Station.class);
        AppUser admin = user(UserRole.ADMIN);
        AppUser supervisor = user(UserRole.SUPERVISOR);
        AppUser operator = user(UserRole.OPERATOR);
        when(userRepository.findAllByRoleInAndActiveTrue(List.of(UserRole.SUPERVISOR, UserRole.ADMIN)))
                .thenReturn(List.of(admin, supervisor));
        when(userRepository.findAllByRoleAndStationAndActiveTrue(UserRole.OPERATOR, station))
                .thenReturn(List.of(operator, admin));

        List<AppUser> recipients = resolver.deviceAlertRecipients(station);

        assertThat(recipients).containsExactly(admin, supervisor, operator);
    }

    @Test
    void deviceAlertRecipientsDoNotIncludeViewersInactiveUsersOrOtherStationOperators() {
        Station station = mock(Station.class);
        AppUser admin = user(UserRole.ADMIN);
        AppUser supervisor = user(UserRole.SUPERVISOR);
        AppUser stationOperator = user(UserRole.OPERATOR);
        AppUser viewer = user(UserRole.VIEWER);
        AppUser inactiveOperator = user(UserRole.OPERATOR);
        inactiveOperator.setActive(false);
        AppUser otherStationOperator = user(UserRole.OPERATOR);
        when(userRepository.findAllByRoleInAndActiveTrue(List.of(UserRole.SUPERVISOR, UserRole.ADMIN)))
                .thenReturn(List.of(admin, supervisor));
        when(userRepository.findAllByRoleAndStationAndActiveTrue(UserRole.OPERATOR, station))
                .thenReturn(List.of(stationOperator));

        List<AppUser> recipients = resolver.deviceAlertRecipients(station);

        assertThat(recipients).containsExactly(admin, supervisor, stationOperator)
                .doesNotContain(viewer, inactiveOperator, otherStationOperator);
    }

    private AppUser user(UserRole role) {
        AppUser user = new AppUser("User", role.name().toLowerCase(), "hash", role, false);
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        return user;
    }
}
