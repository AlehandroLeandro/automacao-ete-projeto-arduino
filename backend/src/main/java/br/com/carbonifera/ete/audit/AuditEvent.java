package br.com.carbonifera.ete.audit;

import br.com.carbonifera.ete.station.Station;
import br.com.carbonifera.ete.user.AppUser;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "audit_events")
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private AuditRecordType recordType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AuditAction action;

    @Column(nullable = false)
    private UUID recordId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "station_id", nullable = false)
    private Station station;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(nullable = false)
    private Instant occurredAt;

    @Column(nullable = false, length = 500)
    private String description;

    @OneToMany(mappedBy = "event", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AuditFieldChange> changes = new ArrayList<>();

    protected AuditEvent() {
    }

    public AuditEvent(
            AuditRecordType recordType,
            AuditAction action,
            UUID recordId,
            Station station,
            AppUser user,
            String description
    ) {
        this.recordType = recordType;
        this.action = action;
        this.recordId = recordId;
        this.station = station;
        this.user = user;
        this.description = description;
    }

    @PrePersist
    void prePersist() {
        occurredAt = Instant.now();
    }

    public void addChange(AuditFieldChange change) {
        change.attachTo(this);
        changes.add(change);
    }

    public UUID getId() {
        return id;
    }

    public AuditRecordType getRecordType() {
        return recordType;
    }

    public AuditAction getAction() {
        return action;
    }

    public UUID getRecordId() {
        return recordId;
    }

    public Station getStation() {
        return station;
    }

    public AppUser getUser() {
        return user;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public String getDescription() {
        return description;
    }

    public List<AuditFieldChange> getChanges() {
        return Collections.unmodifiableList(changes);
    }
}
