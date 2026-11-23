package br.com.ete.device;

import br.com.ete.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/device-keys")
public class DeviceKeyController {

    private final DeviceKeyService service;

    public DeviceKeyController(DeviceKeyService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<DeviceKeyResponse> list() {
        return service.list();
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public DeviceKeyResponse create(@Valid @RequestBody DeviceKeyCreateRequest request,
                                    @AuthenticationPrincipal UserPrincipal principal) {
        return service.create(request.name(), request.stationId(), principal).key();
    }

    @GetMapping("/{id}/secret")
    @PreAuthorize("hasRole('ADMIN')")
    public DeviceKeySecretResponse secret(@PathVariable UUID id) {
        return service.secret(id);
    }

    @PatchMapping("/{id}/active")
    @PreAuthorize("hasRole('ADMIN')")
    public DeviceKeyResponse setActive(@PathVariable UUID id, @Valid @RequestBody DeviceKeyActiveRequest request,
                                       @AuthenticationPrincipal UserPrincipal principal) {
        return service.setActive(id, request.active(), principal);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id, @AuthenticationPrincipal UserPrincipal principal) {
        service.delete(id, principal);
    }
}
