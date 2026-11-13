package br.com.carbonifera.ete.station;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "stations")
public class Station {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 120)
    private String name;

    @Column(nullable = false, unique = true, length = 80)
    private String slug;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "min_lime_stock_bags")
    private BigDecimal minLimeStockBags;

    @Column(name = "lime_min_stock_alerted", nullable = false)
    private boolean limeMinStockAlerted;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected Station() {
    }

    public Station(String name, String slug) {
        this.name = name;
        this.slug = slug;
        this.active = true;
    }

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getSlug() {
        return slug;
    }

    public boolean isActive() {
        return active;
    }

    public BigDecimal getMinLimeStockBags() {
        return minLimeStockBags;
    }

    public boolean isLimeMinStockAlerted() {
        return limeMinStockAlerted;
    }

    public void updateLimeThreshold(BigDecimal minLimeStockBags) {
        this.minLimeStockBags = minLimeStockBags;
        this.limeMinStockAlerted = false;
    }

    public void setLimeMinStockAlerted(boolean limeMinStockAlerted) {
        this.limeMinStockAlerted = limeMinStockAlerted;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
