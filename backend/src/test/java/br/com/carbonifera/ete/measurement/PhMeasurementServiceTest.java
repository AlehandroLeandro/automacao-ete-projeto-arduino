package br.com.carbonifera.ete.measurement;

import br.com.carbonifera.ete.audit.AuditAction;
import br.com.carbonifera.ete.audit.AuditDescriptionRequest;
import br.com.carbonifera.ete.audit.AuditEventResponse;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
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
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PhMeasurementServiceTest {

    private static final Instant CURRENT_TIME = Instant.parse("2026-07-01T10:00:00Z");
    private static final Instant OUTSIDE_WINDOW_TIME = CURRENT_TIME.minusSeconds(30 * 60 * 60);
    private static final String FUTURE_TIME_MESSAGE = "O horário não pode ser maior que o horário atual";
    private static final String NOT_FOUND_MESSAGE = "pH measurement not found";

    @Mock
    private PhMeasurementRepository repository;

    @Mock
    private StationScopeService stationScopeService;

    @Mock
    private CurrentUserService currentUserService;

    @Mock
    private AuditService auditService;

    @Mock
    private UserPrincipal principal;

    private PhMeasurementService service;
    private Station station;
    private AppUser user;
    private AppUser otherOperator;
    private AppUser supervisor;
    private AppUser admin;

    @BeforeEach
    void setUp() {
        service = new PhMeasurementService(repository, stationScopeService, currentUserService, auditService, fixedClock());
        station = station("ITE A");
        user = user(station);
        otherOperator = user(station, UserRole.OPERATOR, "operator-b");
        supervisor = user(station, UserRole.SUPERVISOR, "supervisor");
        admin = user(station, UserRole.ADMIN, "admin");
    }

    @Test
    void createRejectsFutureMeasuredAtBeforeResolvingScope() {
        PhMeasurementRequest request = new PhMeasurementRequest(
                station.getId(),
                PhWaterType.RAW,
                new BigDecimal("7.10"),
                CURRENT_TIME.plusSeconds(1)
        );

        assertThatThrownBy(() -> service.create(request, principal))
                .isInstanceOf(BusinessException.class)
                .hasMessage(FUTURE_TIME_MESSAGE);

        verifyNoInteractions(stationScopeService, currentUserService, repository);
    }

    @Test
    void pagedListRejectsUnsupportedPageSize() {
        assertThatThrownBy(() -> service.list(null, false, null, CURRENT_TIME.minusSeconds(10), CURRENT_TIME, false, null, 0, 25, UserPrincipal.from(admin)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void pagedListUsesOperatorAuthorOverRequestedAuthor() {
        UserPrincipal operator = UserPrincipal.from(user);
        when(stationScopeService.resolveReadableStations(null, false, operator)).thenReturn(List.of(station));
        when(repository.findForList(any(), any(), any(), any(), any(Boolean.class), any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        service.list(null, false, null, CURRENT_TIME.minusSeconds(10), CURRENT_TIME, false, admin.getId(), 0, 10, operator);
        verify(repository).findForList(List.of(station), null, CURRENT_TIME.minusSeconds(10), CURRENT_TIME, false, user.getId(), PageRequest.of(0, 10, Sort.by(Sort.Order.desc("measuredAt"), Sort.Order.desc("id"))));
    }

    @Test
    void pagedListUsesRequestedAuthorForAdmin() {
        UserPrincipal adminPrincipal = UserPrincipal.from(admin);
        when(stationScopeService.resolveReadableStations(null, false, adminPrincipal)).thenReturn(List.of(station));
        when(repository.findForList(any(), any(), any(), any(), any(Boolean.class), any(), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        service.list(null, false, null, CURRENT_TIME.minusSeconds(10), CURRENT_TIME, false, user.getId(), 0, 10, adminPrincipal);
        verify(repository).findForList(List.of(station), null, CURRENT_TIME.minusSeconds(10), CURRENT_TIME, false, user.getId(), PageRequest.of(0, 10, Sort.by(Sort.Order.desc("measuredAt"), Sort.Order.desc("id"))));
    }

    @Test
    void createAllowsMeasuredAtEqualToCurrentTime() {
        PhMeasurementRequest request = new PhMeasurementRequest(
                station.getId(),
                PhWaterType.RAW,
                new BigDecimal("7.10"),
                CURRENT_TIME
        );
        when(stationScopeService.requireWritableStation(station.getId(), principal)).thenReturn(station);
        when(currentUserService.requireUser(principal)).thenReturn(user);
        when(repository.save(any(PhMeasurement.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PhMeasurementResponse response = service.create(request, principal);

        assertThat(response.measuredAt()).isEqualTo(CURRENT_TIME);
        verify(repository).save(any(PhMeasurement.class));
    }

    @Test
    void updateRejectsFutureMeasuredAtBeforeLoadingMeasurement() {
        PhMeasurementUpdateRequest request = new PhMeasurementUpdateRequest(
                station.getId(),
                PhWaterType.RAW,
                new BigDecimal("7.25"),
                CURRENT_TIME.plusSeconds(1),
                "Correcao de leitura"
        );

        assertThatThrownBy(() -> service.update(UUID.randomUUID(), request, principal))
                .isInstanceOf(BusinessException.class)
                .hasMessage(FUTURE_TIME_MESSAGE);

        verifyNoInteractions(repository, stationScopeService, currentUserService, auditService);
    }

    @Test
    void updateRecordsUpdatedEvent() {
        PhMeasurement measurement = phMeasurement(BigDecimal.valueOf(7.10));
        PhMeasurementUpdateRequest request = new PhMeasurementUpdateRequest(
                station.getId(),
                PhWaterType.RAW,
                BigDecimal.valueOf(7.25),
                measurement.getMeasuredAt(),
                "Correcao de leitura"
        );
        when(repository.findByIdAndDeletedFalse(measurement.getId())).thenReturn(Optional.of(measurement));
        doNothing().when(stationScopeService).requireWritableStation(measurement.getStation(), principal);
        when(stationScopeService.requireWritableStation(station.getId(), principal)).thenReturn(station);
        when(currentUserService.requireUser(principal)).thenReturn(user);

        service.update(measurement.getId(), request, principal);

        verify(auditService).recordUpdate(
                AuditRecordType.PH_MEASUREMENT,
                measurement.getId(),
                station,
                user,
                "Correcao de leitura",
                List.of(new AuditFieldChangeDraft("ph", "pH", "7.1", "7.25"))
        );
        assertThat(measurement.getPh()).isEqualByComparingTo("7.25");
    }

    @Test
    void deleteRecordsDeletedEventAndSoftDeletesMeasurement() {
        PhMeasurement measurement = phMeasurement(BigDecimal.valueOf(7.10));
        AuditDescriptionRequest request = new AuditDescriptionRequest("Registro duplicado");
        when(repository.findByIdAndDeletedFalse(measurement.getId())).thenReturn(Optional.of(measurement));
        when(currentUserService.requireUser(principal)).thenReturn(user);

        service.delete(measurement.getId(), request, principal);

        verify(auditService).recordDelete(
                AuditRecordType.PH_MEASUREMENT,
                measurement.getId(),
                station,
                user,
                "Registro duplicado"
        );
        assertThat(measurement.isDeleted()).isTrue();
        assertThat(measurement.getDeletedBy()).isEqualTo(user);
        verify(repository, never()).delete(any());
    }

    @Test
    void updateWithoutRealChangeFailsWithoutAuditLog() {
        PhMeasurement measurement = phMeasurement(new BigDecimal("7.10"));
        PhMeasurementUpdateRequest request = new PhMeasurementUpdateRequest(
                station.getId(),
                measurement.getType(),
                new BigDecimal("7.100"),
                measurement.getMeasuredAt(),
                "Tentativa sem alteracao"
        );
        when(repository.findByIdAndDeletedFalse(measurement.getId())).thenReturn(Optional.of(measurement));
        doNothing().when(stationScopeService).requireWritableStation(measurement.getStation(), principal);
        when(stationScopeService.requireWritableStation(station.getId(), principal)).thenReturn(station);

        assertThatThrownBy(() -> service.update(measurement.getId(), request, principal))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Update must change at least one field");

        verify(auditService, never()).recordUpdate(any(), any(), any(), any(), any(), any());
    }

    @Test
    void detailReturnsDeletedMeasurementHistoryWhenReadable() {
        PhMeasurement measurement = phMeasurement(BigDecimal.valueOf(7.10));
        measurement.softDelete(user);
        AuditEventResponse auditEvent = new AuditEventResponse(
                UUID.randomUUID(),
                AuditRecordType.PH_MEASUREMENT,
                AuditAction.DELETED,
                measurement.getId(),
                station.getId(),
                station.getName(),
                user.getId(),
                user.getName(),
                Instant.parse("2026-07-01T12:00:00Z"),
                "Registro duplicado",
                List.of()
        );
        when(repository.findById(measurement.getId())).thenReturn(Optional.of(measurement));
        when(auditService.history(AuditRecordType.PH_MEASUREMENT, measurement.getId(), principal))
                .thenReturn(List.of(auditEvent));

        PhMeasurementDetailResponse response = service.detail(measurement.getId(), principal);

        verify(stationScopeService).requireReadableStation(station, principal);
        assertThat(response.deleted()).isTrue();
        assertThat(response.edited()).isFalse();
        assertThat(response.changes()).containsExactly(auditEvent);
    }

    @Test
    void listAcceptsOnlyEndAt() {
        Instant endAt = Instant.parse("2026-07-02T00:00:00Z");
        when(stationScopeService.resolveReadableStations(null, false, principal)).thenReturn(List.of(station));
        when(repository.findForList(List.of(station), null, null, endAt, false, null)).thenReturn(List.of());

        assertThat(service.list(
                null,
                false,
                null,
                null,
                endAt,
                false,
                principal
        )).isEmpty();

        verify(repository).findForList(List.of(station), null, null, endAt, false, null);
    }

    @Test
    void listRejectsEndAtBeforeStartAtBeforeResolvingScope() {
        assertThatThrownBy(() -> service.list(
                null,
                false,
                null,
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
    void listPassesScopeTypePeriodAndDeletedFiltersToRepository() {
        Instant startAt = Instant.parse("2026-07-01T00:00:00Z");
        Instant endAt = Instant.parse("2026-07-02T00:00:00Z");
        PhMeasurement measurement = phMeasurement(
                station,
                PhWaterType.TREATED,
                new BigDecimal("6.80"),
                Instant.parse("2026-07-01T10:00:00Z")
        );
        when(stationScopeService.resolveReadableStations(station.getId(), false, principal))
                .thenReturn(List.of(station));
        when(repository.findForList(List.of(station), PhWaterType.TREATED, startAt, endAt, true, null))
                .thenReturn(List.of(measurement));

        List<PhMeasurementResponse> response = service.list(
                station.getId(),
                false,
                PhWaterType.TREATED,
                startAt,
                endAt,
                true,
                principal
        );

        assertThat(response)
                .extracting(PhMeasurementResponse::id)
                .containsExactly(measurement.getId());
        verify(stationScopeService).resolveReadableStations(station.getId(), false, principal);
        verify(repository).findForList(List.of(station), PhWaterType.TREATED, startAt, endAt, true, null);
    }

    @Test
    void listUsesResolvedReadableStationsWhenOperatorRequestsConsolidatedScope() {
        Instant startAt = Instant.parse("2026-07-01T00:00:00Z");
        Instant endAt = Instant.parse("2026-07-02T00:00:00Z");
        UserPrincipal operatorPrincipal = UserPrincipal.from(user);
        PhMeasurement measurement = phMeasurement(BigDecimal.valueOf(7.10));
        when(stationScopeService.resolveReadableStations(null, true, operatorPrincipal)).thenReturn(List.of(station));
        when(repository.findForList(List.of(station), null, startAt, endAt, false, user.getId())).thenReturn(List.of(measurement));

        List<PhMeasurementResponse> response = service.list(
                null,
                true,
                null,
                startAt,
                endAt,
                false,
                operatorPrincipal
        );

        assertThat(response)
                .extracting(PhMeasurementResponse::stationId)
                .containsExactly(station.getId());
        verify(stationScopeService).resolveReadableStations(null, true, operatorPrincipal);
        verify(repository).findForList(List.of(station), null, startAt, endAt, false, user.getId());
    }

    @Test
    void operatorListFiltersByOwnAuthorshipAndKeepsRequestedStartAt() {
        Instant requestedStartAt = Instant.parse("2026-06-01T00:00:00Z");
        Instant endAt = Instant.parse("2026-07-02T00:00:00Z");
        UserPrincipal operatorPrincipal = UserPrincipal.from(user);
        PhMeasurement ownMeasurement = phMeasurement(station, PhWaterType.RAW, new BigDecimal("7.10"), OUTSIDE_WINDOW_TIME);
        when(stationScopeService.resolveReadableStations(station.getId(), false, operatorPrincipal))
                .thenReturn(List.of(station));
        when(repository.findForList(List.of(station), PhWaterType.RAW, requestedStartAt, endAt, true, user.getId()))
                .thenReturn(List.of(ownMeasurement));

        List<PhMeasurementResponse> response = service.list(
                station.getId(), false, PhWaterType.RAW, requestedStartAt, endAt, true, operatorPrincipal);

        assertThat(response)
                .extracting(PhMeasurementResponse::id)
                .containsExactly(ownMeasurement.getId());
        verify(repository).findForList(List.of(station), PhWaterType.RAW, requestedStartAt, endAt, true, user.getId());
    }

    @Test
    void supervisorListIsNotFilteredByAuthorship() {
        Instant startAt = Instant.parse("2026-07-01T00:00:00Z");
        Instant endAt = Instant.parse("2026-07-02T00:00:00Z");
        UserPrincipal supervisorPrincipal = UserPrincipal.from(supervisor);
        PhMeasurement measurementFromOperator = phMeasurement(new BigDecimal("7.10"));
        when(stationScopeService.resolveReadableStations(station.getId(), false, supervisorPrincipal))
                .thenReturn(List.of(station));
        when(repository.findForList(List.of(station), PhWaterType.TREATED, startAt, endAt, false, null))
                .thenReturn(List.of(measurementFromOperator));

        List<PhMeasurementResponse> response = service.list(
                station.getId(), false, PhWaterType.TREATED, startAt, endAt, false, supervisorPrincipal);

        assertThat(response)
                .extracting(PhMeasurementResponse::id)
                .containsExactly(measurementFromOperator.getId());
        verify(repository).findForList(List.of(station), PhWaterType.TREATED, startAt, endAt, false, null);
    }

    @Test
    void adminListIsNotFilteredByAuthorship() {
        Instant startAt = Instant.parse("2026-07-01T00:00:00Z");
        Instant endAt = Instant.parse("2026-07-02T00:00:00Z");
        UserPrincipal adminPrincipal = UserPrincipal.from(admin);
        PhMeasurement measurementFromOperator = phMeasurement(new BigDecimal("7.10"));
        when(stationScopeService.resolveReadableStations(station.getId(), false, adminPrincipal))
                .thenReturn(List.of(station));
        when(repository.findForList(List.of(station), PhWaterType.RAW, startAt, endAt, false, null))
                .thenReturn(List.of(measurementFromOperator));

        List<PhMeasurementResponse> response = service.list(
                station.getId(), false, PhWaterType.RAW, startAt, endAt, false, adminPrincipal);

        assertThat(response)
                .extracting(PhMeasurementResponse::id)
                .containsExactly(measurementFromOperator.getId());
        verify(repository).findForList(List.of(station), PhWaterType.RAW, startAt, endAt, false, null);
    }

    @Test
    void operatorDetailOfForeignMeasurementIsNotFound() {
        PhMeasurement foreign = foreignMeasurement();
        UserPrincipal operatorPrincipal = UserPrincipal.from(user);
        when(repository.findById(foreign.getId())).thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> service.detail(foreign.getId(), operatorPrincipal))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage(NOT_FOUND_MESSAGE);

        verifyNoInteractions(stationScopeService, auditService);
    }

    @Test
    void operatorUpdateOfForeignMeasurementIsNotFoundAndDoesNotChangeAnything() {
        PhMeasurement foreign = foreignMeasurement();
        BigDecimal originalPh = foreign.getPh();
        PhMeasurementUpdateRequest request = new PhMeasurementUpdateRequest(
                station.getId(),
                PhWaterType.TREATED,
                new BigDecimal("6.80"),
                CURRENT_TIME,
                "Correcao do pH"
        );
        UserPrincipal operatorPrincipal = UserPrincipal.from(user);
        when(repository.findByIdAndDeletedFalse(foreign.getId())).thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> service.update(foreign.getId(), request, operatorPrincipal))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage(NOT_FOUND_MESSAGE);

        assertThat(foreign.getPh()).isEqualTo(originalPh);
        assertThat(foreign.getType()).isEqualTo(PhWaterType.RAW);
        verifyNoInteractions(stationScopeService, auditService, currentUserService);
    }

    @Test
    void operatorDeleteOfForeignMeasurementIsNotFoundAndDoesNotSoftDelete() {
        PhMeasurement foreign = foreignMeasurement();
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
        PhMeasurement measurement = phMeasurement(station, PhWaterType.RAW, new BigDecimal("7.10"), OUTSIDE_WINDOW_TIME);
        UserPrincipal operatorPrincipal = UserPrincipal.from(user);
        when(repository.findById(measurement.getId())).thenReturn(Optional.of(measurement));
        when(auditService.history(AuditRecordType.PH_MEASUREMENT, measurement.getId(), operatorPrincipal))
                .thenReturn(List.of());

        PhMeasurementDetailResponse response = service.detail(measurement.getId(), operatorPrincipal);

        assertThat(response.measurement().id()).isEqualTo(measurement.getId());
        assertThat(response.measurement().measuredAt()).isEqualTo(OUTSIDE_WINDOW_TIME);
    }

    @Test
    void operatorUpdatesOwnTreatedMeasurementOutsideRecordWindowToADateAlsoOutsideTheWindow() {
        PhMeasurement measurement = phMeasurement(station, PhWaterType.TREATED, new BigDecimal("6.80"), OUTSIDE_WINDOW_TIME);
        Instant newMeasuredAt = OUTSIDE_WINDOW_TIME.minusSeconds(3600);
        PhMeasurementUpdateRequest request = new PhMeasurementUpdateRequest(
                station.getId(),
                PhWaterType.TREATED,
                new BigDecimal("6.90"),
                newMeasuredAt,
                "Correcao do pH"
        );
        UserPrincipal operatorPrincipal = UserPrincipal.from(user);
        when(repository.findByIdAndDeletedFalse(measurement.getId())).thenReturn(Optional.of(measurement));
        doNothing().when(stationScopeService).requireWritableStation(measurement.getStation(), operatorPrincipal);
        when(stationScopeService.requireWritableStation(station.getId(), operatorPrincipal)).thenReturn(station);
        when(currentUserService.requireUser(operatorPrincipal)).thenReturn(user);

        PhMeasurementResponse response = service.update(measurement.getId(), request, operatorPrincipal);

        assertThat(response.measuredAt()).isEqualTo(newMeasuredAt);
        assertThat(response.ph()).isEqualByComparingTo(new BigDecimal("6.90"));
        assertThat(response.type()).isEqualTo(PhWaterType.TREATED);
    }

    @Test
    void operatorDeletesOwnRawMeasurementOutsideRecordWindow() {
        PhMeasurement measurement = phMeasurement(station, PhWaterType.RAW, new BigDecimal("7.10"), OUTSIDE_WINDOW_TIME);
        UserPrincipal operatorPrincipal = UserPrincipal.from(user);
        when(repository.findByIdAndDeletedFalse(measurement.getId())).thenReturn(Optional.of(measurement));
        doNothing().when(stationScopeService).requireWritableStation(measurement.getStation(), operatorPrincipal);
        when(currentUserService.requireUser(operatorPrincipal)).thenReturn(user);

        service.delete(measurement.getId(), new AuditDescriptionRequest("Exclusao"), operatorPrincipal);

        assertThat(measurement.isDeleted()).isTrue();
    }

    @Test
    void adminUpdatesMeasurementCreatedByAnotherUser() {
        PhMeasurement foreign = foreignMeasurement();
        PhMeasurementUpdateRequest request = new PhMeasurementUpdateRequest(
                station.getId(),
                PhWaterType.RAW,
                new BigDecimal("6.90"),
                foreign.getMeasuredAt(),
                "Correcao do pH"
        );
        UserPrincipal adminPrincipal = UserPrincipal.from(admin);
        when(repository.findByIdAndDeletedFalse(foreign.getId())).thenReturn(Optional.of(foreign));
        doNothing().when(stationScopeService).requireWritableStation(foreign.getStation(), adminPrincipal);
        when(stationScopeService.requireWritableStation(station.getId(), adminPrincipal)).thenReturn(station);
        when(currentUserService.requireUser(adminPrincipal)).thenReturn(admin);

        PhMeasurementResponse response = service.update(foreign.getId(), request, adminPrincipal);

        assertThat(response.ph()).isEqualByComparingTo(new BigDecimal("6.90"));
    }

    @Test
    void supervisorDeletesMeasurementCreatedByAnotherUser() {
        PhMeasurement foreign = foreignMeasurement();
        UserPrincipal supervisorPrincipal = UserPrincipal.from(supervisor);
        when(repository.findByIdAndDeletedFalse(foreign.getId())).thenReturn(Optional.of(foreign));
        doNothing().when(stationScopeService).requireWritableStation(foreign.getStation(), supervisorPrincipal);
        when(currentUserService.requireUser(supervisorPrincipal)).thenReturn(supervisor);

        service.delete(foreign.getId(), new AuditDescriptionRequest("Exclusao"), supervisorPrincipal);

        assertThat(foreign.isDeleted()).isTrue();
    }

    private PhMeasurement phMeasurement(BigDecimal ph) {
        return phMeasurement(station, PhWaterType.RAW, ph, Instant.parse("2026-07-01T10:00:00Z"));
    }

    private PhMeasurement phMeasurement(Station measurementStation, PhWaterType type, BigDecimal ph, Instant measuredAt) {
        return phMeasurement(measurementStation, type, ph, measuredAt, user);
    }

    private PhMeasurement phMeasurement(
            Station measurementStation,
            PhWaterType type,
            BigDecimal ph,
            Instant measuredAt,
            AppUser createdBy
    ) {
        PhMeasurement measurement = new PhMeasurement(measurementStation, type, ph, measuredAt, createdBy);
        ReflectionTestUtils.setField(measurement, "id", UUID.randomUUID());
        return measurement;
    }

    private PhMeasurement foreignMeasurement() {
        return phMeasurement(station, PhWaterType.RAW, new BigDecimal("7.10"), CURRENT_TIME, otherOperator);
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
