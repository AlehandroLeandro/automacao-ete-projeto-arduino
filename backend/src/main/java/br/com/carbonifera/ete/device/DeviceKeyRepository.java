package br.com.carbonifera.ete.device;

import br.com.carbonifera.ete.station.Station;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeviceKeyRepository extends JpaRepository<DeviceKey, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select key from DeviceKey key where key.id = :id")
    Optional<DeviceKey> lockById(@Param("id") UUID id);

    Optional<DeviceKey> findByValueHash(String valueHash);

    boolean existsByStationAndNameKey(Station station, String nameKey);

    List<DeviceKey> findAllByOrderByNameKeyAsc();

    List<DeviceKey> findAllByActiveTrueAndLastSeenAtIsNotNull();

    List<DeviceKey> findAllByStationIn(List<Station> stations);

    List<DeviceKey> findAllByStationInAndActiveTrue(List<Station> stations);
}
