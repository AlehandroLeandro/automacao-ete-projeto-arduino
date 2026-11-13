package br.com.carbonifera.ete.user;

import br.com.carbonifera.ete.common.error.BusinessException;
import br.com.carbonifera.ete.station.StationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock AppUserRepository repository;
    @Mock StationService stationService;
    @Mock PasswordEncoder passwordEncoder;
    @Mock TemporaryPasswordGenerator passwordGenerator;

    @Test
    void viewerMustBeLinkedToStation() {
        when(passwordGenerator.generate()).thenReturn("temp");
        UserCreateRequest request = new UserCreateRequest("Viewer", "viewer", UserRole.VIEWER, null);

        assertThatThrownBy(() -> service().create(request)).isInstanceOf(BusinessException.class);
    }

    @Test
    void directoryIsEmptyWhenNoActiveUsers() {
        when(repository.findAllByActiveTrue()).thenReturn(List.of());
        assertThat(service().listDirectory()).isEmpty();
    }

    @Test
    void directoryMapsAllActiveUsers() {
        AppUser first = user("A", UserRole.ADMIN, true);
        AppUser second = user("B", UserRole.SUPERVISOR, true);
        when(repository.findAllByActiveTrue()).thenReturn(List.of(first, second));
        assertThat(service().listDirectory()).extracting(UserDirectoryEntry::name).containsExactly("A", "B");
    }

    @Test
    void inactiveUsersAreNotMapped() {
        AppUser active = user("A", UserRole.ADMIN, true);
        when(repository.findAllByActiveTrue()).thenReturn(List.of(active));
        assertThat(service().listDirectory()).noneMatch(entry -> entry.name().equals("Inactive"));
    }

    private UserService service() {
        return new UserService(repository, stationService, passwordEncoder, passwordGenerator);
    }

    private AppUser user(String name, UserRole role, boolean active) {
        AppUser user = new AppUser(name, name.toLowerCase(), "hash", role, false);
        user.setActive(active);
        return user;
    }
}
