package br.com.carbonifera.ete.stoppage;

import br.com.carbonifera.ete.station.Station;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StoppageRepository extends JpaRepository<Stoppage, UUID> {

    Optional<Stoppage> findByClientRequestId(UUID clientRequestId);

    Optional<Stoppage> findByIdAndDeletedFalse(UUID stoppageId);

    Optional<Stoppage> findFirstByStationAndStatusAndDeletedFalseOrderByStartAtDesc(
            Station station,
            StoppageStatus status
    );

    List<Stoppage> findAllByStationInAndStatusAndDeletedFalse(Collection<Station> stations, StoppageStatus status);

    @Query("""
            select stoppage from Stoppage stoppage
            where stoppage.station in :stations
              and (:status is null or stoppage.status = :status)
              and (cast(:includeDeleted as boolean) = true or stoppage.deleted = false)
              and (cast(:endAt as timestamp) is null or stoppage.startAt < :endAt)
              and (cast(:startAt as timestamp) is null or stoppage.endAt is null or stoppage.endAt > :startAt)
            order by stoppage.startAt desc, stoppage.id desc
            """)
    List<Stoppage> findForList(
            @Param("stations") Collection<Station> stations,
            @Param("status") StoppageStatus status,
            @Param("startAt") Instant startAt,
            @Param("endAt") Instant endAt,
            @Param("includeDeleted") boolean includeDeleted
    );

    @Query("""
            select stoppage from Stoppage stoppage
            where stoppage.station in :stations
              and (:status is null or stoppage.status = :status)
              and (cast(:includeDeleted as boolean) = true or stoppage.deleted = false)
              and (cast(:endAt as timestamp) is null or stoppage.startAt < :endAt)
              and (cast(:startAt as timestamp) is null or stoppage.endAt is null or stoppage.endAt > :startAt)
              and (:createdById is null or stoppage.createdBy.id = :createdById)
              and (:reason is null or stoppage.reason = :reason)
            order by stoppage.startAt desc, stoppage.id desc
            """)
    Page<Stoppage> findForList(
            @Param("stations") Collection<Station> stations,
            @Param("status") StoppageStatus status,
            @Param("startAt") Instant startAt,
            @Param("endAt") Instant endAt,
            @Param("includeDeleted") boolean includeDeleted,
            @Param("createdById") UUID createdById,
            @Param("reason") StoppageReason reason,
            Pageable pageable
    );

    @Query("""
            select stoppage from Stoppage stoppage
            where stoppage.station in :stations
              and (:status is null or stoppage.status = :status)
              and (cast(:includeDeleted as boolean) = true or stoppage.deleted = false)
              and (cast(:endAt as timestamp) is null or stoppage.startAt < :endAt)
              and (cast(:startAt as timestamp) is null or stoppage.startAt >= :startAt or (stoppage.endAt is null and stoppage.deleted = false))
            order by stoppage.startAt desc, stoppage.id desc
            """)
    List<Stoppage> findForOperatorList(
            @Param("stations") Collection<Station> stations,
            @Param("status") StoppageStatus status,
            @Param("startAt") Instant startAt,
            @Param("endAt") Instant endAt,
            @Param("includeDeleted") boolean includeDeleted
    );

    @Query("""
            select stoppage from Stoppage stoppage
            where stoppage.station in :stations
              and (:status is null or stoppage.status = :status)
              and (cast(:includeDeleted as boolean) = true or stoppage.deleted = false)
              and (cast(:endAt as timestamp) is null or stoppage.startAt < :endAt)
              and (cast(:startAt as timestamp) is null or stoppage.startAt >= :startAt or (stoppage.endAt is null and stoppage.deleted = false))
            order by stoppage.startAt desc, stoppage.id desc
            """)
    Page<Stoppage> findForOperatorList(
            @Param("stations") Collection<Station> stations,
            @Param("status") StoppageStatus status,
            @Param("startAt") Instant startAt,
            @Param("endAt") Instant endAt,
            @Param("includeDeleted") boolean includeDeleted,
            Pageable pageable
    );

    @Query("""
            select stoppage from Stoppage stoppage
            where stoppage.startAt < :endAt
              and stoppage.deleted = false
              and coalesce(stoppage.endAt, :endAt) > :startAt
              and (:stationId is null or stoppage.station.id = :stationId)
            order by stoppage.startAt asc
            """)
    List<Stoppage> findOverlapping(
            @Param("startAt") Instant startAt,
            @Param("endAt") Instant endAt,
            @Param("stationId") UUID stationId
    );

    @Query("""
            select stoppage from Stoppage stoppage
            where stoppage.station.id = :stationId
              and stoppage.deleted = false
              and (:ignoredId is null or stoppage.id <> :ignoredId)
              and stoppage.startAt < :candidateEnd
              and coalesce(stoppage.endAt, :openEnded) > :candidateStart
            order by stoppage.startAt asc
            """)
    List<Stoppage> findOverlapConflicts(
            @Param("stationId") UUID stationId,
            @Param("candidateStart") Instant candidateStart,
            @Param("candidateEnd") Instant candidateEnd,
            @Param("openEnded") Instant openEnded,
            @Param("ignoredId") UUID ignoredId
    );

    @Query("""
            select stoppage from Stoppage stoppage
            where stoppage.station in :stations
              and stoppage.deleted = false
              and stoppage.startAt < :endAt
              and coalesce(stoppage.endAt, :endAt) > :startAt
            order by stoppage.startAt desc
            """)
    List<Stoppage> findRecentForActivity(
            @Param("stations") Collection<Station> stations,
            @Param("startAt") Instant startAt,
            @Param("endAt") Instant endAt
    );
}
