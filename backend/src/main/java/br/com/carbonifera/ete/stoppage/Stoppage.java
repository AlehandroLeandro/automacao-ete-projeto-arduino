package br.com.carbonifera.ete.stoppage;

import br.com.carbonifera.ete.common.error.BusinessException;
import br.com.carbonifera.ete.station.Station;
import br.com.carbonifera.ete.user.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "stoppages")
public class Stoppage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "client_request_id", unique = true)
    private UUID clientRequestId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "station_id", nullable = false)
    private Station station;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 60)
    private StoppageReason reason;

    @Column(length = 200)
    private String reasonDetail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StoppageStatus status;

    @Column(nullable = false)
    private Instant startAt;

    private Instant endAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_id", nullable = false)
    private AppUser createdBy;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private boolean deleted;

    private Instant deletedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deleted_by_id")
    private AppUser deletedBy;

    protected Stoppage() {
    }

    public Stoppage(Station station, StoppageReason reason, Instant startAt, AppUser createdBy) {
        this(station, reason, null, startAt, createdBy);
    }

    public Stoppage(Station station, StoppageReason reason, String reasonDetail, Instant startAt, AppUser createdBy) {
        this.station = station;
        this.reason = reason;
        this.reasonDetail = reasonDetail;
        this.startAt = startAt;
        this.createdBy = createdBy;
        this.status = StoppageStatus.ACTIVE;
    }

    public void setClientRequestId(UUID clientRequestId) { this.clientRequestId = clientRequestId; }

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
    }

    public void finish(Instant endAt) {
        if (status != StoppageStatus.ACTIVE) {
            throw new BusinessException("Stoppage is already finished");
        }
        if (!endAt.isAfter(startAt)) {
            throw new BusinessException("Horário de fim deve ser maior que o horário de início");
        }
        this.endAt = endAt;
        this.status = StoppageStatus.FINISHED;
    }

    public void updateManual(StoppageReason reason, String reasonDetail, Instant startAt, Instant endAt) {
        if (endAt != null && !endAt.isAfter(startAt)) {
            throw new BusinessException("Horário de fim deve ser maior que o horário de início");
        }
        this.reason = reason;
        this.reasonDetail = reasonDetail;
        this.startAt = startAt;
        this.endAt = endAt;
        this.status = endAt == null ? StoppageStatus.ACTIVE : StoppageStatus.FINISHED;
    }

    public void softDelete(AppUser deletedBy) {
        this.deleted = true;
        this.deletedAt = Instant.now();
        this.deletedBy = deletedBy;
    }

    public Duration durationUntil(Instant reference) {
        Instant effectiveEnd = endAt == null ? reference : endAt;
        return Duration.between(startAt, effectiveEnd);
    }

    public UUID getId() {
        return id;
    }

    public Station getStation() {
        return station;
    }

    public StoppageReason getReason() {
        return reason;
    }

    public String getReasonDetail() {
        return reasonDetail;
    }

    public StoppageStatus getStatus() {
        return status;
    }

    public Instant getStartAt() {
        return startAt;
    }

    public Instant getEndAt() {
        return endAt;
    }

    public AppUser getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public AppUser getDeletedBy() {
        return deletedBy;
    }
}
