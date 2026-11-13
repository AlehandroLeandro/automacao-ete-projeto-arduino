package br.com.carbonifera.ete.lime;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import org.springframework.data.domain.Page;

import java.util.UUID;

@RestController
@RequestMapping("/api/lime-records")
public class LimeController {

    private final LimeService service;

    public LimeController(LimeService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('OPERATOR','SUPERVISOR','ADMIN')")
    public Page<LimeRecordListResponse> list(
            @RequestParam(required = false) UUID stationId,
            @RequestParam(defaultValue = "false") boolean consolidated,
            @RequestParam(required = false) LimeRecordType type,
            @RequestParam(required = false) Instant startAt,
            @RequestParam(required = false) Instant endAt,
            @RequestParam(required = false) UUID authorUserId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return service.list(stationId, consolidated, type, startAt, endAt, authorUserId, page, size, principal);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('OPERATOR','ADMIN')")
    public LimeRecordResponse create(
            @Valid @RequestBody LimeRecordRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return service.create(request, principal);
    }

    @PutMapping("/{recordId}")
    @PreAuthorize("hasAnyRole('OPERATOR','ADMIN')")
    public LimeRecordResponse update(
            @PathVariable UUID recordId,
            @Valid @RequestBody LimeRecordRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return service.update(recordId, request, principal);
    }

    @DeleteMapping("/{recordId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('OPERATOR','ADMIN')")
    public void delete(
            @PathVariable UUID recordId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        service.delete(recordId, principal);
    }

    @GetMapping("/current-stock")
    @PreAuthorize("hasAnyRole('OPERATOR','SUPERVISOR','ADMIN')")
    public LimeStockResponse currentStock(
            @RequestParam UUID stationId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return service.currentStock(stationId, Instant.now(), principal);
    }
}
