package br.com.carbonifera.ete.dashboard;

import br.com.carbonifera.ete.security.UserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService service;

    public DashboardController(DashboardService service) {
        this.service = service;
    }

    @GetMapping
    public DashboardResponse dashboard(
            @RequestParam(required = false) UUID stationId,
            @RequestParam(defaultValue = "false") boolean consolidated,
            @RequestParam(required = false) Instant startAt,
            @RequestParam(required = false) Instant endAt,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return service.dashboard(stationId, consolidated, startAt, endAt, principal);
    }
}
