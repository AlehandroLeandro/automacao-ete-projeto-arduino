package br.com.carbonifera.ete.station;

import br.com.carbonifera.ete.security.CurrentUserService;
import br.com.carbonifera.ete.security.UserPrincipal;
import br.com.carbonifera.ete.user.AppUser;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class StationScopeService {

    private final StationService stationService;
    private final CurrentUserService currentUserService;

    public StationScopeService(StationService stationService, CurrentUserService currentUserService) {
        this.stationService = stationService;
        this.currentUserService = currentUserService;
    }

    @Transactional(readOnly = true)
    public Station requireWritableStation(UUID stationId, UserPrincipal principal) {
        Station station = stationService.requireStation(stationId);
        requireWritableStation(station, principal);
        return station;
    }

    @Transactional(readOnly = true)
    public void requireWritableStation(Station station, UserPrincipal principal) {
        requireOperatorStationMatch(station, principal);
    }

    @Transactional(readOnly = true)
    public Station requireReadableStation(UUID stationId, UserPrincipal principal) {
        Station station = stationService.requireStation(stationId);
        requireReadableStation(station, principal);
        return station;
    }

    @Transactional(readOnly = true)
    public void requireReadableStation(Station station, UserPrincipal principal) {
        requireOperatorStationMatch(station, principal);
    }

    @Transactional(readOnly = true)
    public StationScope resolveReportScope(UUID stationId, boolean consolidated, UserPrincipal principal) {
        return resolveReadScope(stationId, consolidated, principal);
    }

    @Transactional(readOnly = true)
    public StationScope resolveReadScope(UUID stationId, boolean consolidated, UserPrincipal principal) {
        if (!principal.getRole().isStationBound()) {
            return new StationScope(stationId, consolidated);
        }

        Station operatorStation = requireOperatorStation(principal);
        rejectDifferentStation(stationId, operatorStation);
        return new StationScope(operatorStation.getId(), false);
    }

    @Transactional(readOnly = true)
    public StationScope resolveDashboardScope(UUID stationId, boolean consolidated, UserPrincipal principal) {
        if (!principal.getRole().isStationBound()) {
            return new StationScope(stationId, consolidated);
        }

        Station operatorStation = requireOperatorStation(principal);
        return new StationScope(operatorStation.getId(), false);
    }

    @Transactional(readOnly = true)
    public List<Station> resolveReadableStations(UUID stationId, boolean consolidated, UserPrincipal principal) {
        StationScope scope = resolveReadScope(stationId, consolidated, principal);
        return stationService.resolveStations(scope.stationId(), scope.consolidated());
    }

    @Transactional(readOnly = true)
    public UUID stationFilterForList(UserPrincipal principal) {
        if (!principal.getRole().isStationBound()) {
            return null;
        }
        return requireOperatorStation(principal).getId();
    }

    private void requireOperatorStationMatch(Station station, UserPrincipal principal) {
        if (!principal.getRole().isStationBound()) {
            return;
        }

        Station operatorStation = requireOperatorStation(principal);
        if (!operatorStation.getId().equals(station.getId())) {
            throw new AccessDeniedException("Station is outside user scope");
        }
    }

    private Station requireOperatorStation(UserPrincipal principal) {
        AppUser user = currentUserService.requireUser(principal);
        Station station = user.getStation();
        if (station == null || !station.isActive()) {
            throw new AccessDeniedException("User must be linked to an active station");
        }
        return station;
    }

    private void rejectDifferentStation(UUID stationId, Station operatorStation) {
        if (stationId != null && !operatorStation.getId().equals(stationId)) {
            throw new AccessDeniedException("Station is outside user scope");
        }
    }
}
