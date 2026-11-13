package br.com.carbonifera.ete.audit;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AuditEventResponse(
        UUID id,
        AuditRecordType recordType,
        AuditAction action,
        UUID recordId,
        UUID stationId,
        String stationName,
        UUID userId,
        String userName,
        Instant occurredAt,
        String description,
        List<AuditFieldChangeResponse> changes
) {

    static AuditEventResponse from(AuditEvent event) {
        return new AuditEventResponse(
                event.getId(),
                event.getRecordType(),
                event.getAction(),
                event.getRecordId(),
                event.getStation().getId(),
                event.getStation().getName(),
                event.getUser().getId(),
                event.getUser().getName(),
                event.getOccurredAt(),
                event.getDescription(),
                event.getChanges()
                        .stream()
                        .map(AuditFieldChangeResponse::from)
                        .toList()
        );
    }
}
