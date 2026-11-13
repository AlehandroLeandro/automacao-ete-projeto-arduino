package br.com.carbonifera.ete.measurement;

import br.com.carbonifera.ete.audit.AuditDescriptionRequest;
import br.com.carbonifera.ete.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.data.domain.Page;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/ph-measurements")
public class PhMeasurementController {

    private final PhMeasurementService service;

    public PhMeasurementController(PhMeasurementService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('OPERATOR','SUPERVISOR','ADMIN')")
    public Page<PhMeasurementResponse> list(
            @RequestParam(required = false) UUID stationId,
            @RequestParam(defaultValue = "false") boolean consolidated,
            @RequestParam(required = false) PhWaterType type,
            @RequestParam(required = false) Instant startAt,
            @RequestParam(required = false) Instant endAt,
            @RequestParam(defaultValue = "false") boolean includeDeleted,
            @RequestParam(required = false) UUID authorUserId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return service.list(stationId, consolidated, type, startAt, endAt, includeDeleted, authorUserId, page, size, principal);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('OPERATOR','ADMIN')")
    public PhMeasurementResponse create(
            @Valid @RequestBody PhMeasurementRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return service.create(request, principal);
    }

    @GetMapping("/{measurementId}")
    @PreAuthorize("hasAnyRole('OPERATOR','SUPERVISOR','ADMIN')")
    public PhMeasurementDetailResponse detail(
            @PathVariable UUID measurementId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return service.detail(measurementId, principal);
    }

    @PutMapping("/{measurementId}")
    @PreAuthorize("hasAnyRole('OPERATOR','ADMIN')")
    public PhMeasurementResponse update(
            @PathVariable UUID measurementId,
            @Valid @RequestBody PhMeasurementUpdateRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return service.update(measurementId, request, principal);
    }

    @DeleteMapping("/{measurementId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('OPERATOR','ADMIN')")
    public void delete(
            @PathVariable UUID measurementId,
            @Valid @RequestBody AuditDescriptionRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        service.delete(measurementId, request, principal);
    }
}
