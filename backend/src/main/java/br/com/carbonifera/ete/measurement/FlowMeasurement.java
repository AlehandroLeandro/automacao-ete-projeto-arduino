package br.com.carbonifera.ete.measurement;

import br.com.carbonifera.ete.station.Station;
import br.com.carbonifera.ete.device.DeviceKey;
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
import java.util.UUID;

@Entity
@Table(name = "flow_measurements", uniqueConstraints = @UniqueConstraint(
        name = "uk_flow_device_window", columnNames = {"device_key_id", "device_window_end"}))
public class FlowMeasurement {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "client_request_id", unique = true)
    private UUID clientRequestId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "station_id", nullable = false)
    private Station station;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal flowLiters;

    @Column(precision = 5, scale = 2)
    private BigDecimal heightCm;

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

    @Column(name = "sample_count")
    private Integer sampleCount;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private boolean deleted;

    private Instant deletedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deleted_by_id")
    private AppUser deletedBy;

    protected FlowMeasurement() {
    }

    public FlowMeasurement(Station station, BigDecimal flowLiters, Instant measuredAt, AppUser createdBy) {
        this.station = station;
        this.flowLiters = flowLiters;
        this.measuredAt = measuredAt;
        this.createdBy = createdBy;
    }

    public static FlowMeasurement fromDevice(Station station, BigDecimal flowLiters, BigDecimal heightCm,
                                             Instant measuredAt, DeviceKey deviceKey, String deviceName,
                                             Instant deviceWindowEnd, Integer sampleCount) {
        FlowMeasurement measurement = new FlowMeasurement(station, flowLiters, measuredAt, null);
        measurement.heightCm = heightCm;
        measurement.deviceKey = deviceKey;
        measurement.deviceName = deviceName;
        measurement.deviceWindowEnd = deviceWindowEnd;
        measurement.sampleCount = sampleCount;
        return measurement;
    }

    public void setClientRequestId(UUID clientRequestId) { this.clientRequestId = clientRequestId; }

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
    }

    public void update(Station station, BigDecimal flowLiters, Instant measuredAt) {
        this.station = station;
        this.flowLiters = flowLiters;
        this.measuredAt = measuredAt;
    }

    public void mergeDeviceReading(BigDecimal flowLiters, BigDecimal heightCm, int sampleCount) {
        this.flowLiters = flowLiters;
        this.heightCm = heightCm;
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

    public BigDecimal getFlowLiters() {
        return flowLiters;
    }

    public BigDecimal getHeightCm() {
        return heightCm;
    }

    public void setHeightCm(BigDecimal heightCm) {
        this.heightCm = heightCm;
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
