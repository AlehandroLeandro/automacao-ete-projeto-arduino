package br.com.carbonifera.ete.stoppage;

import br.com.carbonifera.ete.audit.AuditEventResponse;

import java.util.List;

public record StoppageDetailResponse(
        StoppageResponse stoppage,
        boolean edited,
        boolean deleted,
        List<AuditEventResponse> changes
) {
}
