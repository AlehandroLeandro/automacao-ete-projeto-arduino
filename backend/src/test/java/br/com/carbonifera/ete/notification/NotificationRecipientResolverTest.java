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

    private AppUser user(UserRole role) {
        AppUser user = new AppUser("User", role.name().toLowerCase(), "hash", role, false);
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        return user;
    }
}
