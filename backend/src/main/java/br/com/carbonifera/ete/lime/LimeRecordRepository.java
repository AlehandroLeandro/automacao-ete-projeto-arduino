package br.com.carbonifera.ete.lime;

import br.com.carbonifera.ete.station.Station;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface LimeRecordRepository extends JpaRepository<LimeRecord, UUID> {

    java.util.Optional<LimeRecord> findByClientRequestId(UUID clientRequestId);

    List<LimeRecord> findAllByStationAndRecordedAtLessThanEqualOrderByRecordedAtAsc(Station station, Instant asOf);

    List<LimeRecord> findAllByStationOrderByRecordedAtAsc(Station station);

    @Query("""
            select coalesce(sum(record.quantityBags), 0)
            from LimeRecord record
            where record.type = br.com.carbonifera.ete.lime.LimeRecordType.CONSUMPTION
              and record.recordedAt >= :startAt
              and record.recordedAt < :endAt
              and record.station = :station
            """)
    BigDecimal sumConsumption(
            @Param("station") Station station,
            @Param("startAt") Instant startAt,
            @Param("endAt") Instant endAt
    );

    @Query("""
            select record from LimeRecord record
            where record.recordedAt >= :startAt
              and record.recordedAt < :endAt
              and (:type is null or record.type = :type)
              and (:stationId is null or record.station.id = :stationId)
            order by record.recordedAt asc
            """)
    List<LimeRecord> findReportRows(
            @Param("type") LimeRecordType type,
            @Param("startAt") Instant startAt,
            @Param("endAt") Instant endAt,
            @Param("stationId") UUID stationId
    );

    @Query("""
            select record from LimeRecord record
            where record.type = :type
              and record.station in :stations
              and record.recordedAt >= :startAt
              and record.recordedAt < :endAt
            order by record.station.name asc, record.recordedAt asc
            """)
    List<LimeRecord> findDashboardRows(
            @Param("type") LimeRecordType type,
            @Param("stations") Collection<Station> stations,
            @Param("startAt") Instant startAt,
            @Param("endAt") Instant endAt
    );

    @Query("""
            select record from LimeRecord record
            where record.station in :stations
              and (:type is null or record.type = :type)
              and (cast(:startAt as timestamp) is null or record.recordedAt >= :startAt)
              and (cast(:endAt as timestamp) is null or record.recordedAt < :endAt)
              and (:createdById is null or record.createdBy.id = :createdById)
            order by record.recordedAt desc, record.id desc
            """)
    List<LimeRecord> findForList(
            @Param("stations") Collection<Station> stations,
            @Param("type") LimeRecordType type,
            @Param("startAt") Instant startAt,
            @Param("endAt") Instant endAt,
            @Param("createdById") UUID createdById
    );

    @Query("""
            select record from LimeRecord record
            where record.station in :stations
              and (:type is null or record.type = :type)
              and (cast(:startAt as timestamp) is null or record.recordedAt >= :startAt)
              and (cast(:endAt as timestamp) is null or record.recordedAt < :endAt)
              and (:createdById is null or record.createdBy.id = :createdById)
            order by record.recordedAt desc, record.id desc
            """)
    Page<LimeRecord> findForList(
            @Param("stations") Collection<Station> stations,
            @Param("type") LimeRecordType type,
            @Param("startAt") Instant startAt,
            @Param("endAt") Instant endAt,
            @Param("createdById") UUID createdById,
            Pageable pageable
    );
}
