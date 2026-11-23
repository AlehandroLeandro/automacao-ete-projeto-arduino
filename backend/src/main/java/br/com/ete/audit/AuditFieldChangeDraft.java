package br.com.ete.audit;

public record AuditFieldChangeDraft(
        String name,
        String label,
        String beforeValue,
        String afterValue
) {

    AuditFieldChange toEntity() {
        return new AuditFieldChange(name, label, beforeValue, afterValue);
    }
}
