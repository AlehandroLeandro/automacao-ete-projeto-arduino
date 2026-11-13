package br.com.carbonifera.ete.measurement;

import br.com.carbonifera.ete.station.Station;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PhMeasurementRepository extends JpaRepository<PhMeasurement, UUID> {

    Optional<PhMeasurement> findByClientRequestId(UUID clientRequestId);

    Optional<PhMeasurement> findByIdAndDeletedFalse(UUID measurementId);

    Optional<PhMeasurement> findTopByStationInAndTypeAndDeletedFalseOrderByMeasuredAtDesc(
            Collection<Station> stations,
            PhWaterType type
    );

    default Optional<PhMeasurement> findTopByStationInAndTypeOrderByMeasuredAtDesc(
            Collection<Station> stations,
            PhWaterType type
    ) {
        return findTopByStationInAndTypeAndDeletedFalseOrderByMeasuredAtDesc(stations, type);
    }

    @Query("""
            select measurement from PhMeasurement measurement
            where measurement.type = :type
              and measurement.deleted = false
              and measurement.measuredAt >= :startAt
              and measurement.measuredAt < :endAt
              and (:stationId is null or measurement.station.id = :stationId)
            order by measurement.measuredAt asc
            """)
    List<PhMeasurement> findReportRows(
            @Param("type") PhWaterType type,
            @Param("startAt") Instant startAt,
            @Param("endAt") Instant endAt,
            @Param("stationId") UUID stationId
    );

    @Query("""
            select measurement from PhMeasurement measurement
            where measurement.type = :type
              and measurement.deleted = false
              and measurement.station in :stations
              and measurement.measuredAt >= :startAt
              and measurement.measuredAt < :endAt
            order by measurement.station.name asc, measurement.measuredAt asc
            """)
    List<PhMeasurement> findDashboardRows(
            @Param("type") PhWaterType type,
            @Param("stations") Collection<Station> stations,
            @Param("startAt") Instant startAt,
            @Param("endAt") Instant endAt
    );

    @Query("""
            select measurement from PhMeasurement measurement
            where measurement.station in :stations
              and (:type is null or measurement.type = :type)
              and (cast(:includeDeleted as boolean) = true or measurement.deleted = false)
              and (cast(:startAt as timestamp) is null or measurement.measuredAt >= :startAt)
              and (cast(:endAt as timestamp) is null or measurement.measuredAt < :endAt)
              and (:createdById is null or measurement.createdBy.id = :createdById)
            order by measurement.measuredAt desc, measurement.id desc
            """)
    Page<PhMeasurement> findForList(
            @Param("stations") Collection<Station> stations,
            @Param("type") PhWaterType type,
            @Param("startAt") Instant startAt,
            @Param("endAt") Instant endAt,
            @Param("includeDeleted") boolean includeDeleted,
            @Param("createdById") UUID createdById,
            Pageable pageable
    );

    @Query("""
            select measurement from PhMeasurement measurement
            where measurement.station in :stations
              and (:type is null or measurement.type = :type)
              and (cast(:includeDeleted as boolean) = true or measurement.deleted = false)
              and (cast(:startAt as timestamp) is null or measurement.measuredAt >= :startAt)
              and (cast(:endAt as timestamp) is null or measurement.measuredAt < :endAt)
              and (:createdById is null or measurement.createdBy.id = :createdById)
            order by measurement.measuredAt desc, measurement.id desc
            """)
    List<PhMeasurement> findForList(
            @Param("stations") Collection<Station> stations,
            @Param("type") PhWaterType type,
            @Param("startAt") Instant startAt,
            @Param("endAt") Instant endAt,
            @Param("includeDeleted") boolean includeDeleted,
            @Param("createdById") UUID createdById
    );
}
