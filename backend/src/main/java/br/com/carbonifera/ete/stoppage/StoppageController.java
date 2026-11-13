package br.com.carbonifera.ete.stoppage;

import br.com.carbonifera.ete.audit.AuditDescriptionRequest;
import br.com.carbonifera.ete.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/stoppages")
public class StoppageController {

    private final StoppageService service;

    public StoppageController(StoppageService service) {
        this.service = service;
    }

    @PostMapping("/start")
    @PreAuthorize("hasAnyRole('OPERATOR','ADMIN')")
    public StoppageResponse start(
            @Valid @RequestBody StoppageStartRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return service.start(request, principal);
    }

    @PostMapping("/{stoppageId}/finish")
    @PreAuthorize("hasAnyRole('OPERATOR','ADMIN')")
    public StoppageResponse finish(
            @PathVariable UUID stoppageId,
            @Valid @RequestBody StoppageFinishRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return service.finish(stoppageId, request, principal);
    }

    @DeleteMapping("/{stoppageId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('OPERATOR','ADMIN')")
    public void delete(
            @PathVariable UUID stoppageId,
            @Valid @RequestBody AuditDescriptionRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        service.delete(stoppageId, request, principal);
    }

    @PutMapping("/{stoppageId}")
    @PreAuthorize("hasAnyRole('OPERATOR','ADMIN')")
    public StoppageResponse update(
            @PathVariable UUID stoppageId,
            @Valid @RequestBody StoppageUpdateRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return service.update(stoppageId, request, principal);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('OPERATOR','SUPERVISOR','ADMIN','VIEWER')")
    public Page<StoppageResponse> list(
            @RequestParam(required = false) UUID stationId,
            @RequestParam(defaultValue = "false") boolean consolidated,
            @RequestParam(required = false) StoppageStatus status,
            @RequestParam(required = false) Instant startAt,
            @RequestParam(required = false) Instant endAt,
            @RequestParam(defaultValue = "false") boolean includeDeleted,
            @RequestParam(required = false) UUID authorUserId,
            @RequestParam(required = false) StoppageReason reason,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return service.list(stationId, consolidated, status, startAt, endAt, includeDeleted, authorUserId, reason, page, size, principal);
    }

    @GetMapping("/active")
    public List<StoppageResponse> active(
            @RequestParam(required = false) UUID stationId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return service.active(stationId, principal);
    }

    @GetMapping("/{stoppageId}/shift-slices")
    public List<StoppageShiftSliceResponse> shiftSlices(
            @PathVariable UUID stoppageId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return service.shiftSlices(stoppageId, principal);
    }

    @GetMapping("/{stoppageId}")
    @PreAuthorize("hasAnyRole('OPERATOR','SUPERVISOR','ADMIN','VIEWER')")
    public StoppageDetailResponse detail(
            @PathVariable UUID stoppageId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return service.detail(stoppageId, principal);
    }
}
