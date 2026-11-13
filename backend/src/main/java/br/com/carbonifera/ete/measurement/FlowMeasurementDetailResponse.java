package br.com.carbonifera.ete.measurement;

import br.com.carbonifera.ete.audit.AuditEventResponse;

import java.util.List;

public record FlowMeasurementDetailResponse(
        FlowMeasurementResponse measurement,
        boolean edited,
        boolean deleted,
        List<AuditEventResponse> changes
) {
}
