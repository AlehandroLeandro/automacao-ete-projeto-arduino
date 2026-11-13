package br.com.carbonifera.ete.station;

import br.com.carbonifera.ete.common.error.ResourceNotFoundException;
import br.com.carbonifera.ete.lime.LimeMinStockMonitor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class StationService {

    private final StationRepository stationRepository;
    private final LimeMinStockMonitor limeMinStockMonitor;

    public StationService(StationRepository stationRepository, LimeMinStockMonitor limeMinStockMonitor) {
        this.stationRepository = stationRepository;
        this.limeMinStockMonitor = limeMinStockMonitor;
    }

    @Transactional(readOnly = true)
    public List<StationResponse> listActive() {
        return stationRepository.findAllByActiveTrueOrderByNameAsc()
                .stream()
                .map(StationResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public Station requireStation(UUID stationId) {
        return stationRepository.findById(stationId)
                .filter(Station::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Station not found"));
    }

    @Transactional
    public StationResponse updateLimeThreshold(UUID stationId, BigDecimal minLimeStockBags) {
        Station station = requireStation(stationId);
        station.updateLimeThreshold(minLimeStockBags);
        limeMinStockMonitor.evaluate(station);
        return StationResponse.from(station);
    }

    @Transactional(readOnly = true)
    public List<Station> resolveStations(UUID stationId, boolean consolidated) {
        if (stationId != null && !consolidated) {
            return List.of(requireStation(stationId));
        }
        return stationRepository.findAllByActiveTrueOrderByNameAsc();
    }
}
