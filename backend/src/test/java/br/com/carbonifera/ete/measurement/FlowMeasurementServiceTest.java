package br.com.carbonifera.ete.measurement;

import br.com.carbonifera.ete.audit.AuditDescriptionRequest;
import br.com.carbonifera.ete.audit.AuditFieldChangeDraft;
import br.com.carbonifera.ete.audit.AuditRecordType;
import br.com.carbonifera.ete.audit.AuditService;
import br.com.carbonifera.ete.common.error.BusinessException;
import br.com.carbonifera.ete.common.error.ResourceNotFoundException;
import br.com.carbonifera.ete.security.CurrentUserService;
import br.com.carbonifera.ete.security.UserPrincipal;
import br.com.carbonifera.ete.station.Station;
import br.com.carbonifera.ete.station.StationScopeService;
import br.com.carbonifera.ete.user.AppUser;
import br.com.carbonifera.ete.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FlowMeasurementServiceTest {

    private static final Instant CURRENT_TIME = Instant.parse("2026-07-01T10:00:00Z");
    private static final Instant OUTSIDE_WINDOW_TIME = CURRENT_TIME.minusSeconds(30 * 60 * 60);
    private static final String FUTURE_TIME_MESSAGE = "O horário não pode ser maior que o horário atual";
    private static final String NOT_FOUND_MESSAGE = "Flow measurement not found";

    @Mock
    private FlowMeasurementRepository repository;

    @Mock
    private StationScopeService stationScopeService;

    @Mock
    private CurrentUserService currentUserService;

    @Mock
    private AuditService auditService;

    @Mock
    private UserPrincipal principal;

    private FlowMeasurementService service;
    private Station station;
    private AppUser user;
    private AppUser otherOperator;
    private AppUser supervisor;
    private AppUser admin;

    @BeforeEach
    void setUp() {
        service = new FlowMeasurementService(repository, stationScopeService, currentUserService, auditService, fixedClock());
        station = station("ITE A");
        user = user(station);
        otherOperator = user(station, UserRole.OPERATOR, "operator-b");
        supervisor = user(station, UserRole.SUPERVISOR, "supervisor");
        admin = user(station, UserRole.ADMIN, "admin");
    }

    @Test
    void createRejectsFutureMeasuredAtBeforeResolvingScope() {
        FlowMeasurementRequest request = new FlowMeasurementRequest(
                station.getId(),
                new BigDecimal("10.00"),
                CURRENT_TIME.plusSeconds(1)
        );

        assertThatThrownBy(() -> service.create(request, principal))
                .isInstanceOf(BusinessException.class)
                .hasMessage(FUTURE_TIME_MESSAGE);

        verifyNoInteractions(stationScopeService, currentUserService, repository);
    }

    @Test
    void createCalculatesAndStoresFlowFromHeight() {
        FlowMeasurementRequest request = new FlowMeasurementRequest(
                station.getId(),
                new BigDecimal("10.00"),
                CURRENT_TIME
        );
        when(stationScopeService.requireWritableStation(station.getId(), principal)).thenReturn(station);
        when(currentUserService.requireUser(principal)).thenReturn(user);
        when(repository.save(any(FlowMeasurement.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.create(request, principal);

        ArgumentCaptor<FlowMeasurement> measurementCaptor = ArgumentCaptor.forClass(FlowMeasurement.class);
        verify(repository).save(measurementCaptor.capture());
        assertThat(measurementCaptor.getValue().getHeightCm()).isEqualByComparingTo("10.00");
        assertThat(measurementCaptor.getValue().getFlowLiters()).isEqualByComparingTo("19.76");
    }

    @Test
    void createFloorsCalculatedFlowAtZero() {
        FlowMeasurementRequest request = new FlowMeasurementRequest(
                station.getId(),
                BigDecimal.ZERO,
                CURRENT_TIME
        );
        when(stationScopeService.requireWritableStation(station.getId(), principal)).thenReturn(station);
        when(currentUserService.requireUser(principal)).thenReturn(user);
        when(repository.save(any(FlowMeasurement.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.create(request, principal);

        ArgumentCaptor<FlowMeasurement> measurementCaptor = ArgumentCaptor.forClass(FlowMeasurement.class);
        verify(repository).save(measurementCaptor.capture());
        assertThat(measurementCaptor.getValue().getHeightCm()).isEqualByComparingTo("0");
        assertThat(measurementCaptor.getValue().getFlowLiters()).isEqualByComparingTo("0.00");
    }

    @Test
    void updateRejectsFutureMeasuredAtBeforeLoadingMeasurement() {
        FlowMeasurementUpdateRequest request = new FlowMeasurementUpdateRequest(
                station.getId(),
                new BigDecimal("10.00"),
                CURRENT_TIME.plusSeconds(1),
                "Correcao da vazao"
        );

        assertThatThrownBy(() -> service.update(UUID.randomUUID(), request, principal))
                .isInstanceOf(BusinessException.class)
                .hasMessage(FUTURE_TIME_MESSAGE);

        verifyNoInteractions(repository, stationScopeService, currentUserService, auditService);
    }

    @Test
    void updateAllowsMeasuredAtEqualToCurrentTime() {
        FlowMeasurement measurement = flowMeasurement(new BigDecimal("1200.00"));
        FlowMeasurementUpdateRequest request = new FlowMeasurementUpdateRequest(
                station.getId(),
                new BigDecimal("10.00"),
                CURRENT_TIME,
                "Correcao da vazao"
        );
        when(repository.findByIdAndDeletedFalse(measurement.getId())).thenReturn(Optional.of(measurement));
        doNothing().when(stationScopeService).requireWritableStation(measurement.getStation(), principal);
        when(stationScopeService.requireWritableStation(station.getId(), principal)).thenReturn(station);
        when(currentUserService.requireUser(principal)).thenReturn(user);

        FlowMeasurementResponse response = service.update(measurement.getId(), request, principal);

        assertThat(response.measuredAt()).isEqualTo(CURRENT_TIME);
    }

    @Test
    void updateWithHeightRecalculatesFlowAndRecordsBothChanges() {
        FlowMeasurement measurement = flowMeasurement(new BigDecimal("1200.00"));
        FlowMeasurementUpdateRequest request = new FlowMeasurementUpdateRequest(
                station.getId(),
                new BigDecimal("10.00"),
                measurement.getMeasuredAt(),
                "Correcao da vazao"
        );
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AuditFieldChangeDraft>> changesCaptor = ArgumentCaptor.forClass(List.class);
        when(repository.findByIdAndDeletedFalse(measurement.getId())).thenReturn(Optional.of(measurement));
        doNothing().when(stationScopeService).requireWritableStation(measurement.getStation(), principal);
        when(stationScopeService.requireWritableStation(station.getId(), principal)).thenReturn(station);
        when(currentUserService.requireUser(principal)).thenReturn(user);

        service.update(measurement.getId(), request, principal);

        verify(auditService).recordUpdate(
                eq(AuditRecordType.FLOW_MEASUREMENT),
                eq(measurement.getId()),
                eq(station),
                eq(user),
                eq("Correcao da vazao"),
                changesCaptor.capture()
        );
        assertThat(changesCaptor.getValue())
                .containsExactly(
                        new AuditFieldChangeDraft("heightCm", "Altura (cm)", null, "10"),
                        new AuditFieldChangeDraft("flowLiters", "Vazao (m³/h)", "1200", "19.76")
                );
        assertThat(measurement.getHeightCm()).isEqualByComparingTo("10.00");
        assertThat(measurement.getFlowLiters()).isEqualByComparingTo("19.76");
    }

    @Test
    void updateWithoutHeightKeepsLegacyHeightAndFlow() {
        FlowMeasurement measurement = flowMeasurement(new BigDecimal("1200.00"));
        FlowMeasurementUpdateRequest request = new FlowMeasurementUpdateRequest(
                station.getId(),
                null,
                measurement.getMeasuredAt().minusSeconds(60),
                "Correcao da data"
        );
        when(repository.findByIdAndDeletedFalse(measurement.getId())).thenReturn(Optional.of(measurement));
        doNothing().when(stationScopeService).requireWritableStation(measurement.getStation(), principal);
        when(stationScopeService.requireWritableStation(station.getId(), principal)).thenReturn(station);
        when(currentUserService.requireUser(principal)).thenReturn(user);

        service.update(measurement.getId(), request, principal);

        assertThat(measurement.getHeightCm()).isNull();
        assertThat(measurement.getFlowLiters()).isEqualByComparingTo("1200.00");
    }

    @Test
    void listAcceptsOnlyEndAt() {
        Instant endAt = Instant.parse("2026-07-02T00:00:00Z");
        when(stationScopeService.resolveReadableStations(null, false, principal)).thenReturn(List.of(station));
        when(repository.findForList(List.of(station), null, endAt, false, null)).thenReturn(List.of());

        assertThat(service.list(
                null,
                false,
                null,
                endAt,
                false,
                principal
        )).isEmpty();

        verify(repository).findForList(List.of(station), null, endAt, false, null);
    }

    @Test
    void listRejectsEndAtBeforeStartAtBeforeResolvingScope() {
        assertThatThrownBy(() -> service.list(
                null,
                false,
                Instant.parse("2026-07-02T00:00:00Z"),
                Instant.parse("2026-07-01T00:00:00Z"),
                false,
                principal
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Horário de fim deve ser maior que o horário de início");

        verifyNoInteractions(stationScopeService, repository);
    }

    @Test
    void listPassesScopePeriodAndDeletedFiltersToRepository() {
        Instant startAt = Instant.parse("2026-07-01T00:00:00Z");
        Instant endAt = Instant.parse("2026-07-02T00:00:00Z");
        FlowMeasurement measurement = flowMeasurement(
                station,
                new BigDecimal("1350.50"),
                Instant.parse("2026-07-01T10:00:00Z")
        );
        when(stationScopeService.resolveReadableStations(station.getId(), false, principal))
                .thenReturn(List.of(station));
        when(repository.findForList(List.of(station), startAt, endAt, true, null)).thenReturn(List.of(measurement));

        List<FlowMeasurementResponse> response = service.list(
                station.getId(),
                false,
                startAt,
                endAt,
                true,
                principal
        );

        assertThat(response)
                .extracting(FlowMeasurementResponse::id)
                .containsExactly(measurement.getId());
        verify(stationScopeService).resolveReadableStations(station.getId(), false, principal);
        verify(repository).findForList(List.of(station), startAt, endAt, true, null);
    }

    @Test
    void operatorListFiltersByOwnAuthorshipAndKeepsRequestedStartAt() {
        Instant requestedStartAt = Instant.parse("2026-06-01T00:00:00Z");
        Instant endAt = Instant.parse("2026-07-02T00:00:00Z");
        UserPrincipal operatorPrincipal = UserPrincipal.from(user);
        FlowMeasurement ownMeasurement = flowMeasurement(
                station,
                new BigDecimal("1200.00"),
                OUTSIDE_WINDOW_TIME
        );
        when(stationScopeService.resolveReadableStations(station.getId(), false, operatorPrincipal))
                .thenReturn(List.of(station));
        when(repository.findForList(List.of(station), requestedStartAt, endAt, true, user.getId()))
                .thenReturn(List.of(ownMeasurement));

        List<FlowMeasurementResponse> response =
                service.list(station.getId(), false, requestedStartAt, endAt, true, operatorPrincipal);

        assertThat(response)
                .extracting(FlowMeasurementResponse::id)
                .containsExactly(ownMeasurement.getId());
        verify(repository).findForList(List.of(station), requestedStartAt, endAt, true, user.getId());
    }

    @Test
    void supervisorListIsNotFilteredByAuthorship() {
        Instant startAt = Instant.parse("2026-07-01T00:00:00Z");
        Instant endAt = Instant.parse("2026-07-02T00:00:00Z");
        UserPrincipal supervisorPrincipal = UserPrincipal.from(supervisor);
        FlowMeasurement measurementFromOperator = flowMeasurement(new BigDecimal("1200.00"));
        when(stationScopeService.resolveReadableStations(station.getId(), false, supervisorPrincipal))
                .thenReturn(List.of(station));
        when(repository.findForList(List.of(station), startAt, endAt, false, null))
                .thenReturn(List.of(measurementFromOperator));

        List<FlowMeasurementResponse> response =
                service.list(station.getId(), false, startAt, endAt, false, supervisorPrincipal);

        assertThat(response)
                .extracting(FlowMeasurementResponse::id)
                .containsExactly(measurementFromOperator.getId());
        verify(repository).findForList(List.of(station), startAt, endAt, false, null);
    }

    @Test
    void adminListIsNotFilteredByAuthorship() {
        Instant startAt = Instant.parse("2026-07-01T00:00:00Z");
        Instant endAt = Instant.parse("2026-07-02T00:00:00Z");
        UserPrincipal adminPrincipal = UserPrincipal.from(admin);
        FlowMeasurement measurementFromOperator = flowMeasurement(new BigDecimal("1200.00"));
        when(stationScopeService.resolveReadableStations(station.getId(), false, adminPrincipal))
                .thenReturn(List.of(station));
        when(repository.findForList(List.of(station), startAt, endAt, false, null))
                .thenReturn(List.of(measurementFromOperator));

        List<FlowMeasurementResponse> response =
                service.list(station.getId(), false, startAt, endAt, false, adminPrincipal);

        assertThat(response)
                .extracting(FlowMeasurementResponse::id)
                .containsExactly(measurementFromOperator.getId());
        verify(repository).findForList(List.of(station), startAt, endAt, false, null);
    }

    @Test
    void operatorDetailOfForeignMeasurementIsNotFound() {
        FlowMeasurement foreign = foreignMeasurement();
        UserPrincipal operatorPrincipal = UserPrincipal.from(user);
        when(repository.findById(foreign.getId())).thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> service.detail(foreign.getId(), operatorPrincipal))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage(NOT_FOUND_MESSAGE);

        verifyNoInteractions(stationScopeService, auditService);
    }

    @Test
    void operatorUpdateOfForeignMeasurementIsNotFoundAndDoesNotChangeAnything() {
        FlowMeasurement foreign = foreignMeasurement();
        BigDecimal originalFlow = foreign.getFlowLiters();
        FlowMeasurementUpdateRequest request = new FlowMeasurementUpdateRequest(
                station.getId(),
                new BigDecimal("10.00"),
                CURRENT_TIME,
                "Correcao da vazao"
        );
        UserPrincipal operatorPrincipal = UserPrincipal.from(user);
        when(repository.findByIdAndDeletedFalse(foreign.getId())).thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> service.update(foreign.getId(), request, operatorPrincipal))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage(NOT_FOUND_MESSAGE);

        assertThat(foreign.getFlowLiters()).isEqualTo(originalFlow);
        verifyNoInteractions(stationScopeService, auditService, currentUserService);
    }

    @Test
    void operatorDeleteOfForeignMeasurementIsNotFoundAndDoesNotSoftDelete() {
        FlowMeasurement foreign = foreignMeasurement();
        UserPrincipal operatorPrincipal = UserPrincipal.from(user);
        when(repository.findByIdAndDeletedFalse(foreign.getId())).thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> service.delete(foreign.getId(), new AuditDescriptionRequest("Exclusao"), operatorPrincipal))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage(NOT_FOUND_MESSAGE);

        assertThat(foreign.isDeleted()).isFalse();
        verifyNoInteractions(stationScopeService, auditService, currentUserService);
    }

    @Test
    void operatorDetailsOwnMeasurementOutsideRecordWindow() {
        FlowMeasurement measurement = flowMeasurement(station, new BigDecimal("1200.00"), OUTSIDE_WINDOW_TIME);
        UserPrincipal operatorPrincipal = UserPrincipal.from(user);
        when(repository.findById(measurement.getId())).thenReturn(Optional.of(measurement));
        when(auditService.history(AuditRecordType.FLOW_MEASUREMENT, measurement.getId(), operatorPrincipal))
                .thenReturn(List.of());

        FlowMeasurementDetailResponse response = service.detail(measurement.getId(), operatorPrincipal);

        assertThat(response.measurement().id()).isEqualTo(measurement.getId());
        assertThat(response.measurement().measuredAt()).isEqualTo(OUTSIDE_WINDOW_TIME);
    }

    @Test
    void operatorUpdatesOwnMeasurementOutsideRecordWindowToADateAlsoOutsideTheWindow() {
        FlowMeasurement measurement = flowMeasurement(station, new BigDecimal("1200.00"), OUTSIDE_WINDOW_TIME);
        Instant newMeasuredAt = OUTSIDE_WINDOW_TIME.minusSeconds(3600);
        FlowMeasurementUpdateRequest request = new FlowMeasurementUpdateRequest(
                station.getId(),
                new BigDecimal("10.00"),
                newMeasuredAt,
                "Correcao da vazao"
        );
        UserPrincipal operatorPrincipal = UserPrincipal.from(user);
        when(repository.findByIdAndDeletedFalse(measurement.getId())).thenReturn(Optional.of(measurement));
        doNothing().when(stationScopeService).requireWritableStation(measurement.getStation(), operatorPrincipal);
        when(stationScopeService.requireWritableStation(station.getId(), operatorPrincipal)).thenReturn(station);
        when(currentUserService.requireUser(operatorPrincipal)).thenReturn(user);

        FlowMeasurementResponse response = service.update(measurement.getId(), request, operatorPrincipal);

        assertThat(response.measuredAt()).isEqualTo(newMeasuredAt);
        assertThat(response.flowLiters()).isEqualByComparingTo(new BigDecimal("19.76"));
    }

    @Test
    void operatorDeletesOwnMeasurementOutsideRecordWindow() {
        FlowMeasurement measurement = flowMeasurement(station, new BigDecimal("1200.00"), OUTSIDE_WINDOW_TIME);
        UserPrincipal operatorPrincipal = UserPrincipal.from(user);
        when(repository.findByIdAndDeletedFalse(measurement.getId())).thenReturn(Optional.of(measurement));
        doNothing().when(stationScopeService).requireWritableStation(measurement.getStation(), operatorPrincipal);
        when(currentUserService.requireUser(operatorPrincipal)).thenReturn(user);

        service.delete(measurement.getId(), new AuditDescriptionRequest("Exclusao"), operatorPrincipal);

        assertThat(measurement.isDeleted()).isTrue();
    }

    @Test
    void adminUpdatesMeasurementCreatedByAnotherUser() {
        FlowMeasurement foreign = foreignMeasurement();
        FlowMeasurementUpdateRequest request = new FlowMeasurementUpdateRequest(
                station.getId(),
                new BigDecimal("10.00"),
                foreign.getMeasuredAt(),
                "Correcao da vazao"
        );
        UserPrincipal adminPrincipal = UserPrincipal.from(admin);
        when(repository.findByIdAndDeletedFalse(foreign.getId())).thenReturn(Optional.of(foreign));
        doNothing().when(stationScopeService).requireWritableStation(foreign.getStation(), adminPrincipal);
        when(stationScopeService.requireWritableStation(station.getId(), adminPrincipal)).thenReturn(station);
        when(currentUserService.requireUser(adminPrincipal)).thenReturn(admin);

        FlowMeasurementResponse response = service.update(foreign.getId(), request, adminPrincipal);

        assertThat(response.flowLiters()).isEqualByComparingTo(new BigDecimal("19.76"));
    }

    @Test
    void supervisorDeletesMeasurementCreatedByAnotherUser() {
        FlowMeasurement foreign = foreignMeasurement();
        UserPrincipal supervisorPrincipal = UserPrincipal.from(supervisor);
        when(repository.findByIdAndDeletedFalse(foreign.getId())).thenReturn(Optional.of(foreign));
        doNothing().when(stationScopeService).requireWritableStation(foreign.getStation(), supervisorPrincipal);
        when(currentUserService.requireUser(supervisorPrincipal)).thenReturn(supervisor);

        service.delete(foreign.getId(), new AuditDescriptionRequest("Exclusao"), supervisorPrincipal);

        assertThat(foreign.isDeleted()).isTrue();
    }

    @Test
    void listUsesResolvedReadableStationsWhenOperatorRequestsConsolidatedScope() {
        Instant startAt = Instant.parse("2026-07-01T00:00:00Z");
        Instant endAt = Instant.parse("2026-07-02T00:00:00Z");
        UserPrincipal operatorPrincipal = UserPrincipal.from(user);
        FlowMeasurement measurement = flowMeasurement(new BigDecimal("1200.00"));
        when(stationScopeService.resolveReadableStations(null, true, operatorPrincipal)).thenReturn(List.of(station));
        when(repository.findForList(List.of(station), startAt, endAt, false, user.getId())).thenReturn(List.of(measurement));

        List<FlowMeasurementResponse> response = service.list(
                null,
                true,
                startAt,
                endAt,
                false,
                operatorPrincipal
        );

        assertThat(response)
                .extracting(FlowMeasurementResponse::stationId)
                .containsExactly(station.getId());
        verify(stationScopeService).resolveReadableStations(null, true, operatorPrincipal);
        verify(repository).findForList(List.of(station), startAt, endAt, false, user.getId());
    }

    private FlowMeasurement flowMeasurement(BigDecimal flowLiters) {
        return flowMeasurement(station, flowLiters, Instant.parse("2026-07-01T10:00:00Z"));
    }

    private FlowMeasurement flowMeasurement(Station measurementStation, BigDecimal flowLiters, Instant measuredAt) {
        return flowMeasurement(measurementStation, flowLiters, measuredAt, user);
    }

    private FlowMeasurement flowMeasurement(
            Station measurementStation,
            BigDecimal flowLiters,
            Instant measuredAt,
            AppUser createdBy
    ) {
        FlowMeasurement measurement = new FlowMeasurement(measurementStation, flowLiters, measuredAt, createdBy);
        ReflectionTestUtils.setField(measurement, "id", UUID.randomUUID());
        return measurement;
    }

    private FlowMeasurement foreignMeasurement() {
        return flowMeasurement(station, new BigDecimal("1200.00"), CURRENT_TIME, otherOperator);
    }

    private Station station(String name) {
        Station createdStation = new Station(name, name.toLowerCase().replace(" ", "-"));
        ReflectionTestUtils.setField(createdStation, "id", UUID.randomUUID());
        return createdStation;
    }

    private AppUser user(Station userStation) {
        return user(userStation, UserRole.OPERATOR, "operator");
    }

    private AppUser user(Station userStation, UserRole role, String username) {
        AppUser appUser = new AppUser(username, username, "hash", role, userStation, false);
        ReflectionTestUtils.setField(appUser, "id", UUID.randomUUID());
        return appUser;
    }

    private Clock fixedClock() {
        return Clock.fixed(CURRENT_TIME, ZoneOffset.UTC);
    }
}
