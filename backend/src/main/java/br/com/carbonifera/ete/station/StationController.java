package br.com.carbonifera.ete.station;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/stations")
public class StationController {

    private final StationService stationService;

    public StationController(StationService stationService) {
        this.stationService = stationService;
    }

    @GetMapping
    public List<StationResponse> list() {
        return stationService.listActive();
    }

    @PutMapping("/{id}/lime-threshold")
    @PreAuthorize("hasAnyRole('SUPERVISOR','ADMIN')")
    public StationResponse updateLimeThreshold(
            @PathVariable UUID id,
            @Valid @RequestBody LimeThresholdRequest request
    ) {
        return stationService.updateLimeThreshold(id, request.minLimeStockBags());
    }
}
