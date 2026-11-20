package br.com.carbonifera.ete.device;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "device_readings", indexes = {
        @Index(name = "idx_device_reading_key_type_measured_at", columnList = "device_key_id, type, measured_at")
})
public class DeviceReading {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "device_key_id", nullable = false)
    private DeviceKey deviceKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DeviceReadingType type;

    @Column(name = "reading_value", nullable = false, precision = 14, scale = 2)
    private BigDecimal value;

    @Column(precision = 14, scale = 2)
    private BigDecimal flowLiters;

    @Column(nullable = false)
    private Instant measuredAt;

    @Column(length = 20)
    private String timeEstimateOrigin;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected DeviceReading() {
    }

    public DeviceReading(DeviceKey deviceKey, DeviceReadingType type, BigDecimal value, BigDecimal flowLiters,
                         Instant measuredAt) {
        this.deviceKey = deviceKey;
        this.type = type;
        this.value = value;
        this.flowLiters = flowLiters;
        this.measuredAt = measuredAt;
    }

    public void setTimeEstimateOrigin(String origin) { this.timeEstimateOrigin = origin; }
    public String getTimeEstimateOrigin() { return timeEstimateOrigin; }

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public DeviceKey getDeviceKey() { return deviceKey; }
    public DeviceReadingType getType() { return type; }
    public BigDecimal getValue() { return value; }
    public BigDecimal getFlowLiters() { return flowLiters; }
    public Instant getMeasuredAt() { return measuredAt; }
    public Instant getCreatedAt() { return createdAt; }
}
