package br.com.carbonifera.ete.lime;

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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "lime_records")
public class LimeRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "client_request_id", unique = true)
    private UUID clientRequestId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "station_id", nullable = false)
    private Station station;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private LimeRecordType type;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal quantityBags;

    @Column(nullable = false)
    private Instant recordedAt;

    @Column(length = 500)
    private String note;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_id", nullable = false)
    private AppUser createdBy;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected LimeRecord() {
    }

    public LimeRecord(
            Station station,
            LimeRecordType type,
            BigDecimal quantityBags,
            Instant recordedAt,
            String note,
            AppUser createdBy
    ) {
        this.station = station;
        this.type = type;
        this.quantityBags = quantityBags;
        this.recordedAt = recordedAt;
        this.note = note;
        this.createdBy = createdBy;
    }

    public void setClientRequestId(UUID clientRequestId) { this.clientRequestId = clientRequestId; }

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
    }

    public void update(Station station, LimeRecordType type, BigDecimal quantityBags, Instant recordedAt, String note) {
        this.station = station;
        this.type = type;
        this.quantityBags = quantityBags;
        this.recordedAt = recordedAt;
        this.note = note;
    }

    public UUID getId() {
        return id;
    }

    public Station getStation() {
        return station;
    }

    public LimeRecordType getType() {
        return type;
    }

    public BigDecimal getQuantityBags() {
        return quantityBags;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }

    public String getNote() {
        return note;
    }

    public AppUser getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
