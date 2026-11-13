package br.com.carbonifera.ete.user;

import br.com.carbonifera.ete.station.Station;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

    Optional<AppUser> findByUsernameIgnoreCase(String username);

    boolean existsByUsernameIgnoreCase(String username);

    List<AppUser> findAllByActiveTrue();

    List<AppUser> findAllByRoleAndActiveTrue(UserRole role);

    List<AppUser> findAllByRoleInAndActiveTrue(List<UserRole> roles);

    List<AppUser> findAllByRoleAndStationAndActiveTrue(UserRole role, Station station);
}
