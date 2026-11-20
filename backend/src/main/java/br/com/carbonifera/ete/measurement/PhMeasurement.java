package br.com.carbonifera.ete.measurement;

import br.com.carbonifera.ete.device.DeviceKey;
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
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ph_measurements", uniqueConstraints = @UniqueConstraint(
        name = "uk_ph_device_type_window", columnNames = {"device_key_id", "device_water_type", "device_window_end"}))
public class PhMeasurement {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "client_request_id", unique = true)
    private UUID clientRequestId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "station_id", nullable = false)
    private Station station;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PhWaterType type;

    @Column(nullable = false, precision = 4, scale = 2)
    private BigDecimal ph;

    @Column(nullable = false)
    private Instant measuredAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_id")
    private AppUser createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_key_id")
    private DeviceKey deviceKey;

    @Column(name = "device_name")
    private String deviceName;

    @Column(name = "device_window_end", updatable = false)
    private Instant deviceWindowEnd;

    // SPEC_DEVIATION: window uniqueness uses the immutable source type instead of editable type.
    // Reason: human type corrections must not lose or collide with another stream's window.
    @Enumerated(EnumType.STRING)
    @Column(name = "device_water_type", length = 20, updatable = false)
    private PhWaterType deviceWaterType;

    @Column(name = "sample_count")
    private Integer sampleCount;

    @Column(length = 20)
    private String timeEstimateOrigin;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private boolean deleted;

    private Instant deletedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deleted_by_id")
    private AppUser deletedBy;

    protected PhMeasurement() {
    }

    public PhMeasurement(Station station, PhWaterType type, BigDecimal ph, Instant measuredAt, AppUser createdBy) {
        this.station = station;
        this.type = type;
        this.ph = ph;
        this.measuredAt = measuredAt;
        this.createdBy = createdBy;
    }

    public static PhMeasurement fromDevice(Station station, PhWaterType type, BigDecimal ph, Instant measuredAt,
                                           DeviceKey deviceKey, String deviceName, Instant deviceWindowEnd,
                                           Integer sampleCount) {
        PhMeasurement measurement = new PhMeasurement(station, type, ph, measuredAt, null);
        measurement.deviceKey = deviceKey;
        measurement.deviceName = deviceName;
        measurement.deviceWindowEnd = deviceWindowEnd;
        measurement.deviceWaterType = type;
        measurement.sampleCount = sampleCount;
        return measurement;
    }

    public boolean isTimeEstimated() { return timeEstimateOrigin != null; }
    public String getTimeEstimateOrigin() { return timeEstimateOrigin; }
    public void mergeTimeEstimate(String origin) {
        if (origin != null) timeEstimateOrigin = timeEstimateOrigin == null || timeEstimateOrigin.equals(origin)
                ? origin : "MIXED";
    }

    public void setClientRequestId(UUID clientRequestId) { this.clientRequestId = clientRequestId; }

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
    }

    public void update(Station station, PhWaterType type, BigDecimal ph, Instant measuredAt) {
        this.station = station;
        this.type = type;
        this.ph = ph;
        this.measuredAt = measuredAt;
        this.timeEstimateOrigin = null;
    }

    public void mergeDeviceReading(BigDecimal ph, int sampleCount) {
        this.ph = ph;
        this.sampleCount = sampleCount;
    }

    public void softDelete(AppUser deletedBy) {
        this.deleted = true;
        this.deletedAt = Instant.now();
        this.deletedBy = deletedBy;
    }

    public UUID getId() {
        return id;
    }

    public Station getStation() {
        return station;
    }

    public PhWaterType getType() {
        return type;
    }

    public BigDecimal getPh() {
        return ph;
    }

    public Instant getMeasuredAt() {
        return measuredAt;
    }

    public AppUser getCreatedBy() {
        return createdBy;
    }

    public DeviceKey getDeviceKey() { return deviceKey; }

    public String getDeviceName() { return deviceName; }

    public Instant getDeviceWindowEnd() { return deviceWindowEnd; }

    public PhWaterType getDeviceWaterType() { return deviceWaterType; }

    public Integer getSampleCount() { return sampleCount; }

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
