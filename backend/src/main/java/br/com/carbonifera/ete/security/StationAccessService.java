package br.com.carbonifera.ete.security;

import br.com.carbonifera.ete.station.Station;
import br.com.carbonifera.ete.station.StationScope;
import br.com.carbonifera.ete.station.StationScopeService;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@Deprecated(forRemoval = false)
public class StationAccessService {

    private final StationScopeService stationScopeService;

    public StationAccessService(StationScopeService stationScopeService) {
        this.stationScopeService = stationScopeService;
    }

    public Station writableStation(UserPrincipal principal, UUID requestedStationId) {
        return stationScopeService.requireWritableStation(requestedStationId, principal);
    }

    public UUID readableStationId(UserPrincipal principal, UUID requestedStationId, boolean consolidated) {
        return reportScope(principal, requestedStationId, consolidated).stationId();
    }

    public boolean readableConsolidated(UserPrincipal principal, boolean consolidated) {
        return reportScope(principal, null, consolidated).consolidated();
    }

    public void assertCanAccessStation(UserPrincipal principal, Station station) {
        stationScopeService.requireReadableStation(station, principal);
    }

    private StationScope reportScope(UserPrincipal principal, UUID requestedStationId, boolean consolidated) {
        return stationScopeService.resolveReportScope(requestedStationId, consolidated, principal);
    }
}
