package br.com.carbonifera.ete.station;

import br.com.carbonifera.ete.security.CurrentUserService;
import br.com.carbonifera.ete.security.UserPrincipal;
import br.com.carbonifera.ete.user.AppUser;
import br.com.carbonifera.ete.user.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StationScopeServiceTest {

    @Test
    void requireWritableStationAllowsAdminForAnyStation() {
        Station station = station(UUID.randomUUID());
        StationScopeService service = service(station, null);
        UserPrincipal principal = principal(UserRole.ADMIN, null);

        Station writableStation = service.requireWritableStation(station.getId(), principal);

        assertSame(station, writableStation);
    }

    @Test
    void requireWritableStationRejectsDifferentStationForOperator() {
        Station assignedStation = station(UUID.randomUUID());
        Station requestedStation = station(UUID.randomUUID());
        AppUser operator = user(UserRole.OPERATOR, assignedStation);
        StationScopeService service = service(requestedStation, operator);
        UserPrincipal principal = UserPrincipal.from(operator);

        assertThrows(
                AccessDeniedException.class,
                () -> service.requireWritableStation(requestedStation.getId(), principal)
        );
    }

    @Test
    void resolveReportScopeLimitsOperatorToAssignedStation() {
        Station assignedStation = station(UUID.randomUUID());
        AppUser operator = user(UserRole.OPERATOR, assignedStation);
        StationScopeService service = service(assignedStation, operator);
        UserPrincipal principal = UserPrincipal.from(operator);

        StationScope scope = service.resolveReportScope(null, true, principal);

        assertEquals(assignedStation.getId(), scope.stationId());
        assertFalse(scope.consolidated());
    }

    @Test
    void resolveReadableStationsAllowsAdminAndSupervisorToReadAllStations() {
        Station firstStation = station(UUID.randomUUID());
        Station secondStation = station(UUID.randomUUID());
        StationScopeService service = service(List.of(firstStation, secondStation), null);

        List<Station> adminStations = service.resolveReadableStations(null, true, principal(UserRole.ADMIN, null));
        List<Station> supervisorStations = service.resolveReadableStations(null, true, principal(UserRole.SUPERVISOR, null));

        assertIterableEquals(List.of(firstStation, secondStation), adminStations);
        assertIterableEquals(List.of(firstStation, secondStation), supervisorStations);
    }

    @Test
    void resolveReadableStationsLimitsOperatorToAssignedStation() {
        Station assignedStation = station(UUID.randomUUID());
        Station secondStation = station(UUID.randomUUID());
        AppUser operator = user(UserRole.OPERATOR, assignedStation);
        StationScopeService service = service(List.of(assignedStation, secondStation), operator);

        List<Station> stations = service.resolveReadableStations(null, true, UserPrincipal.from(operator));

        assertIterableEquals(List.of(assignedStation), stations);
    }

    @Test
    void resolveReadableStationsRejectsOperatorWithoutActiveStation() {
        AppUser operator = user(UserRole.OPERATOR, null);
        StationScopeService service = service(List.of(station(UUID.randomUUID())), operator);

        assertThrows(
                AccessDeniedException.class,
                () -> service.resolveReadableStations(null, true, UserPrincipal.from(operator))
        );
    }

    @Test
    void resolveReadableStationsLimitsViewerToAssignedStation() {
        Station assignedStation = station(UUID.randomUUID());
        Station secondStation = station(UUID.randomUUID());
        AppUser viewer = user(UserRole.VIEWER, assignedStation);
        StationScopeService service = service(List.of(assignedStation, secondStation), viewer);

        List<Station> stations = service.resolveReadableStations(null, true, UserPrincipal.from(viewer));

        assertIterableEquals(List.of(assignedStation), stations);
    }

    @Test
    void requireReadableStationRejectsDifferentStationForViewer() {
        Station assignedStation = station(UUID.randomUUID());
        Station requestedStation = station(UUID.randomUUID());
        AppUser viewer = user(UserRole.VIEWER, assignedStation);
        StationScopeService service = service(List.of(assignedStation, requestedStation), viewer);
        UserPrincipal principal = UserPrincipal.from(viewer);

        assertThrows(
                AccessDeniedException.class,
                () -> service.requireReadableStation(requestedStation.getId(), principal)
        );
    }

    private UserPrincipal principal(UserRole role, Station station) {
        return UserPrincipal.from(user(role, station));
    }

    private AppUser user(UserRole role, Station station) {
        return new AppUser("User", role.name().toLowerCase(), "hash", role, station, false);
    }

    private Station station(UUID stationId) {
        Station station = new Station("Station " + stationId, "station-" + stationId);
        ReflectionTestUtils.setField(station, "id", stationId);
        return station;
    }

    private StationScopeService service(Station station, AppUser currentUser) {
        return service(List.of(station), currentUser);
    }

    private StationScopeService service(List<Station> stations, AppUser currentUser) {
        return new StationScopeService(new FakeStationService(stations), new FakeCurrentUserService(currentUser));
    }

    private static class FakeStationService extends StationService {

        private final List<Station> stations;

        FakeStationService(List<Station> stations) {
            super(null, null);
            this.stations = stations;
        }

        @Override
        public Station requireStation(UUID stationId) {
            return stations.stream()
                    .filter(station -> station.getId().equals(stationId))
                    .findFirst()
                    .orElseThrow();
        }

        @Override
        public List<Station> resolveStations(UUID stationId, boolean consolidated) {
            if (stationId != null && !consolidated) {
                return List.of(requireStation(stationId));
            }
            return stations;
        }
    }

    private static class FakeCurrentUserService extends CurrentUserService {

        private final AppUser currentUser;

        FakeCurrentUserService(AppUser currentUser) {
            super(null);
            this.currentUser = currentUser;
        }

        @Override
        public AppUser requireUser(UserPrincipal principal) {
            return currentUser;
        }
    }
}
