package br.com.carbonifera.ete.audit;

import br.com.carbonifera.ete.security.UserPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/audit-events")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping("/{recordType}/{recordId}")
    @PreAuthorize("hasAnyRole('OPERATOR','SUPERVISOR','ADMIN')")
    public List<AuditEventResponse> history(
            @PathVariable AuditRecordType recordType,
            @PathVariable UUID recordId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return auditService.history(recordType, recordId, principal);
    }
}
