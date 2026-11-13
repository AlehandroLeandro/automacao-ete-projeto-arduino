package br.com.carbonifera.ete.user;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class AppUserRepositoryTest {

    @Autowired
    private AppUserRepository repository;

    @Test
    void findAllByRoleAndActiveTrueReturnsOnlyActiveUsersOfTheRole() {
        AppUser activeSupervisor = repository.save(user("Active Sup", "active-sup", UserRole.SUPERVISOR, true));
        repository.save(user("Inactive Sup", "inactive-sup", UserRole.SUPERVISOR, false));
        repository.save(user("Active Op", "active-op", UserRole.OPERATOR, true));

        List<AppUser> supervisors = repository.findAllByRoleAndActiveTrue(UserRole.SUPERVISOR);

        assertThat(supervisors)
                .extracting(AppUser::getId)
                .containsExactly(activeSupervisor.getId());
    }

    private AppUser user(String name, String username, UserRole role, boolean active) {
        AppUser user = new AppUser(name, username, "hash", role, false);
        user.setActive(active);
        return user;
    }
}
