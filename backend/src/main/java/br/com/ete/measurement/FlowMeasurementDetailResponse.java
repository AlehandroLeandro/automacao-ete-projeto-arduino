package br.com.ete.measurement;

import br.com.ete.audit.AuditEventResponse;

import java.util.List;

public record FlowMeasurementDetailResponse(
        FlowMeasurementResponse measurement,
        boolean edited,
        boolean deleted,
        List<AuditEventResponse> changes
) {
}
