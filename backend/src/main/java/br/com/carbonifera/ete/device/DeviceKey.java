package br.com.carbonifera.ete.device;

import br.com.carbonifera.ete.station.Station;
import br.com.carbonifera.ete.user.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "device_keys", uniqueConstraints = {
        @UniqueConstraint(name = "uk_device_key_station_name", columnNames = {"station_id", "name_key"}),
        @UniqueConstraint(name = "uk_device_key_value_hash", columnNames = "value_hash")
})
public class DeviceKey {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "station_id", nullable = false)
    private Station station;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "name_key", nullable = false, length = 120)
    private String nameKey;

    @Column(name = "encrypted_value", nullable = false, length = 512)
    private String encryptedValue;

    @Column(name = "value_hash", nullable = false, unique = true, length = 64)
    private String valueHash;

    @Column(nullable = false)
    private boolean active;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_id", nullable = false)
    private AppUser createdBy;

    private Instant lastSeenAt;

    @Column(precision = 5, scale = 2)
    private BigDecimal lastHeightCm;

    @Column(precision = 14, scale = 2)
    private BigDecimal lastFlowLiters;

    private Instant lastFlowAt;

    @Column(precision = 4, scale = 2)
    private BigDecimal lastRawPh;

    private Instant lastRawPhAt;

    @Column(precision = 4, scale = 2)
    private BigDecimal lastTreatedPh;

    private Instant lastTreatedPhAt;
    private String lastFlowTimeEstimateOrigin;
    private String lastRawPhTimeEstimateOrigin;
    private String lastTreatedPhTimeEstimateOrigin;

    private Instant offlineNotifiedForSeenAt;

    protected DeviceKey() {
    }

    public DeviceKey(Station station, String name, String encryptedValue, String valueHash, AppUser createdBy) {
        this.station = station;
        this.name = name;
        this.nameKey = name.toLowerCase(Locale.ROOT);
        this.encryptedValue = encryptedValue;
        this.valueHash = valueHash;
        this.createdBy = createdBy;
        this.active = true;
    }

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
    }

    public void deactivate() {
        active = false;
    }

    public void activate() {
        active = true;
    }

    public void recordSeen(Instant seenAt) {
        lastSeenAt = seenAt;
    }

    public void markOfflineNotified(Instant seenAt) {
        offlineNotifiedForSeenAt = seenAt;
    }

    public void clearOfflineNotified() {
        offlineNotifiedForSeenAt = null;
    }

    public void recordLiveFlow(BigDecimal heightCm, BigDecimal flowLiters, Instant measuredAt) {
        if (heightCm != null) {
            lastHeightCm = heightCm;
            lastFlowAt = measuredAt;
        }
        if (flowLiters != null) {
            lastFlowLiters = flowLiters;
            lastFlowAt = measuredAt;
        }
    }

    public void recordLivePh(BigDecimal rawPh, BigDecimal treatedPh, Instant measuredAt) {
        if (rawPh != null) {
            lastRawPh = rawPh;
            lastRawPhAt = measuredAt;
        }
        if (treatedPh != null) {
            lastTreatedPh = treatedPh;
            lastTreatedPhAt = measuredAt;
        }
    }

    public UUID getId() { return id; }
    public Station getStation() { return station; }
    public String getName() { return name; }
    public String getNameKey() { return nameKey; }
    public String getEncryptedValue() { return encryptedValue; }
    public String getValueHash() { return valueHash; }
    public boolean isActive() { return active; }
    public Instant getCreatedAt() { return createdAt; }
    public AppUser getCreatedBy() { return createdBy; }
    public Instant getLastSeenAt() { return lastSeenAt; }
    public BigDecimal getLastHeightCm() { return lastHeightCm; }
    public void setLastFlowTimeEstimateOrigin(String origin) { lastFlowTimeEstimateOrigin = origin; }
    public void setLastRawPhTimeEstimateOrigin(String origin) { lastRawPhTimeEstimateOrigin = origin; }
    public void setLastTreatedPhTimeEstimateOrigin(String origin) { lastTreatedPhTimeEstimateOrigin = origin; }
    public String getLastFlowTimeEstimateOrigin() { return lastFlowTimeEstimateOrigin; }
    public String getLastRawPhTimeEstimateOrigin() { return lastRawPhTimeEstimateOrigin; }
    public String getLastTreatedPhTimeEstimateOrigin() { return lastTreatedPhTimeEstimateOrigin; }
    public BigDecimal getLastFlowLiters() { return lastFlowLiters; }
    public Instant getLastFlowAt() { return lastFlowAt; }
    public BigDecimal getLastRawPh() { return lastRawPh; }
    public Instant getLastRawPhAt() { return lastRawPhAt; }
    public BigDecimal getLastTreatedPh() { return lastTreatedPh; }
    public Instant getLastTreatedPhAt() { return lastTreatedPhAt; }
    public Instant getOfflineNotifiedForSeenAt() { return offlineNotifiedForSeenAt; }
}
