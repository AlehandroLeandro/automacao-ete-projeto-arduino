package br.com.carbonifera.ete.device;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface DeviceReadingRepository extends JpaRepository<DeviceReading, UUID> {

    @Query("""
            select reading from DeviceReading reading
            where reading.measuredAt < :hourStart
            order by reading.measuredAt asc
            """)
    @EntityGraph(attributePaths = {"deviceKey", "deviceKey.station"})
    List<DeviceReading> findPendingBefore(@Param("hourStart") Instant hourStart);

    @Query("""
            select reading from DeviceReading reading
            where reading.deviceKey = :deviceKey
              and reading.type = :type
              and reading.measuredAt >= :startAt
              and reading.measuredAt < :endAt
            order by reading.measuredAt asc
            """)
    List<DeviceReading> findByDeviceKeyAndTypeAndMeasuredAtBetween(
            @Param("deviceKey") DeviceKey deviceKey,
            @Param("type") DeviceReadingType type,
            @Param("startAt") Instant startAt,
            @Param("endAt") Instant endAt
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("delete from DeviceReading reading where reading.deviceKey = :deviceKey")
    int deleteByDeviceKey(@Param("deviceKey") DeviceKey deviceKey);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("delete from DeviceReading reading where reading.id in :ids")
    int deleteAllByIdIn(@Param("ids") Collection<UUID> ids);
}
