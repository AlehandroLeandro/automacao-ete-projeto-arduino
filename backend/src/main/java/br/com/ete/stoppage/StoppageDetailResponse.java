package br.com.ete.stoppage;

import br.com.ete.audit.AuditEventResponse;

import java.util.List;

public record StoppageDetailResponse(
        StoppageResponse stoppage,
        boolean edited,
        boolean deleted,
        List<AuditEventResponse> changes
) {
}
