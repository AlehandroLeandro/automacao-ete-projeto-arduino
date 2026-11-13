package br.com.carbonifera.ete.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "audit_field_changes")
public class AuditFieldChange {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private AuditEvent event;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(nullable = false, length = 120)
    private String label;

    @Column(length = 500)
    private String beforeValue;

    @Column(length = 500)
    private String afterValue;

    protected AuditFieldChange() {
    }

    AuditFieldChange(String name, String label, String beforeValue, String afterValue) {
        this.name = name;
        this.label = label;
        this.beforeValue = beforeValue;
        this.afterValue = afterValue;
    }

    void attachTo(AuditEvent event) {
        this.event = event;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getLabel() {
        return label;
    }

    public String getBeforeValue() {
        return beforeValue;
    }

    public String getAfterValue() {
        return afterValue;
    }
}
