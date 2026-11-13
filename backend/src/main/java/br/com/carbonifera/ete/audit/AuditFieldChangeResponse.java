package br.com.carbonifera.ete.audit;

public record AuditFieldChangeResponse(
        String name,
        String label,
        String beforeValue,
        String afterValue
) {

    static AuditFieldChangeResponse from(AuditFieldChange change) {
        return new AuditFieldChangeResponse(
                change.getName(),
                change.getLabel(),
                change.getBeforeValue(),
                change.getAfterValue()
        );
    }
}
