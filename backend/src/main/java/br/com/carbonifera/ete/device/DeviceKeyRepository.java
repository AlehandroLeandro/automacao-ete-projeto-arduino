package br.com.carbonifera.ete.device;

import br.com.carbonifera.ete.station.Station;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeviceKeyRepository extends JpaRepository<DeviceKey, UUID> {

    Optional<DeviceKey> findByValueHash(String valueHash);

    boolean existsByStationAndNameKey(Station station, String nameKey);

    List<DeviceKey> findAllByOrderByNameKeyAsc();

    List<DeviceKey> findAllByActiveTrueAndLastSeenAtIsNotNull();

    List<DeviceKey> findAllByStationInAndActiveTrue(List<Station> stations);
}
