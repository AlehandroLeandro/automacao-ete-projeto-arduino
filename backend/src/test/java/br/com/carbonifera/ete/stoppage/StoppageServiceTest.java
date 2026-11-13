package br.com.carbonifera.ete.stoppage;

import br.com.carbonifera.ete.audit.AuditAction;
import br.com.carbonifera.ete.audit.AuditDescriptionRequest;
import br.com.carbonifera.ete.audit.AuditEventResponse;
import br.com.carbonifera.ete.audit.AuditFieldChangeDraft;
import br.com.carbonifera.ete.audit.AuditRecordType;
import br.com.carbonifera.ete.audit.AuditService;
import br.com.carbonifera.ete.common.error.BusinessException;
import br.com.carbonifera.ete.notification.NotificationCommand;
import br.com.carbonifera.ete.notification.NotificationDispatcher;
import br.com.carbonifera.ete.notification.NotificationRecipientResolver;
import br.com.carbonifera.ete.notification.NotificationType;
import br.com.carbonifera.ete.security.CurrentUserService;
import br.com.carbonifera.ete.security.OperatorWindowPolicy;
import br.com.carbonifera.ete.security.UserPrincipal;
import br.com.carbonifera.ete.station.Station;
import br.com.carbonifera.ete.station.StationScopeService;
import br.com.carbonifera.ete.user.AppUser;
import br.com.carbonifera.ete.user.UserRole;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class StoppageServiceTest {

    private static final Instant CURRENT_TIME = Instant.parse("2026-07-01T10:00:00Z");
    private static final String FUTURE_TIME_MESSAGE = "O horário não pode ser maior que o horário atual";
    private static final String OVERLAP_MESSAGE = "Já existe uma parada registrada para esta ETE nesse intervalo";

    @Test
    void pagedListRejectsUnsupportedPageSize() {
        StoppageRepository repository = mock(StoppageRepository.class);
        StationScopeService stationScopeService = mock(StationScopeService.class);
        StoppageService service = service(repository, stationScopeService);

        assertThatThrownBy(() -> service.list(null, false, null, CURRENT_TIME.minusSeconds(10), CURRENT_TIME,
                false, null, null, 0, 20, UserPrincipal.from(user(UserRole.ADMIN, null))))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Page size must be one of: 10, 50, 100");
        verifyNoInteractions(stationScopeService, repository);
    }

    @Test
    void pagedListPassesAuthorAndReasonFilters() {
        Station station = station(UUID.randomUUID(), "ITE A");
        AppUser author = user(UserRole.ADMIN, null);
        UserPrincipal principal = UserPrincipal.from(author);
        Instant startAt = CURRENT_TIME.minusSeconds(100);
        Instant endAt = CURRENT_TIME;
        StoppageRepository repository = mock(StoppageRepository.class);
        StationScopeService stationScopeService = mock(StationScopeService.class);
        when(stationScopeService.resolveReadableStations(null, false, principal)).thenReturn(List.of(station));
        when(repository.findForList(any(), any(), eq(startAt), eq(endAt), eq(false), eq(author.getId()),
                eq(StoppageReason.FALTA_DE_AGUA), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));
        StoppageService service = service(repository, stationScopeService);

        service.list(null, false, null, startAt, endAt, false, author.getId(), StoppageReason.FALTA_DE_AGUA, 0, 10, principal);

        verify(repository).findForList(List.of(station), null, startAt, endAt, false, author.getId(),
                StoppageReason.FALTA_DE_AGUA, org.springframework.data.domain.PageRequest.of(0, 10,
                        org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Order.desc("startAt"),
                                org.springframework.data.domain.Sort.Order.desc("id"))));
    }

    @Test
    void pagedListMapsPageContent() {
        Station station = station(UUID.randomUUID(), "ITE A");
        AppUser author = user(UserRole.ADMIN, null);
        UserPrincipal principal = UserPrincipal.from(author);
        Stoppage stoppage = stoppage(UUID.randomUUID(), station, StoppageReason.OUTROS, CURRENT_TIME.minusSeconds(10), null, author);
        StoppageRepository repository = mock(StoppageRepository.class);
        StationScopeService stationScopeService = mock(StationScopeService.class);
        when(stationScopeService.resolveReadableStations(null, false, principal)).thenReturn(List.of(station));
        when(repository.findForList(any(), any(), any(), any(), eq(false), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(stoppage)));

        assertThat(service(repository, stationScopeService).list(null, false, null, CURRENT_TIME.minusSeconds(20), CURRENT_TIME,
                false, null, null, 0, 10, principal).getContent()).extracting(StoppageResponse::id).containsExactly(stoppage.getId());
    }

    @Test
    void pagedOperatorListUsesWindowQueryWithoutAuthorOrReason() {
        Station station = station(UUID.randomUUID(), "ITE A");
        AppUser operator = user(UserRole.OPERATOR, station);
        UserPrincipal principal = UserPrincipal.from(operator);
        Instant startAt = CURRENT_TIME.minusSeconds(100000);
        Instant endAt = CURRENT_TIME;
        Instant lowerBound = CURRENT_TIME.minusSeconds(86400);
        StoppageRepository repository = mock(StoppageRepository.class);
        StationScopeService stationScopeService = mock(StationScopeService.class);
        when(stationScopeService.resolveReadableStations(station.getId(), false, principal)).thenReturn(List.of(station));
        when(repository.findForOperatorList(any(), any(), eq(lowerBound), eq(endAt), eq(false), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
        StoppageService service = new StoppageService(repository, stationScopeService, mock(CurrentUserService.class),
                mock(ShiftCalculator.class), mock(AuditService.class), mock(NotificationDispatcher.class),
                mock(NotificationRecipientResolver.class), fixedClock(), OperatorWindowPolicy.defaultWindow(fixedClock()));

        service.list(station.getId(), false, null, startAt, endAt, false, UUID.randomUUID(), StoppageReason.OUTROS, 0, 10, principal);

        verify(repository).findForOperatorList(List.of(station), null, lowerBound, endAt, false,
                org.springframework.data.domain.PageRequest.of(0, 10,
                        org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Order.desc("startAt"),
                                org.springframework.data.domain.Sort.Order.desc("id"))));
    }

    @Test
    void startRejectsOutrosWithoutFreeTextDescription() {
        StoppageRepository repository = mock(StoppageRepository.class);
        StationScopeService stationScopeService = mock(StationScopeService.class);
        CurrentUserService currentUserService = mock(CurrentUserService.class);
        Station station = station(UUID.randomUUID(), "ITE A");
        AppUser user = user(UserRole.OPERATOR, station);
        UserPrincipal principal = UserPrincipal.from(user);
        StoppageStartRequest request = new StoppageStartRequest(
                station.getId(),
                StoppageReason.OUTROS,
                "   ",
                CURRENT_TIME
        );
        when(stationScopeService.requireWritableStation(station.getId(), principal)).thenReturn(station);
        when(repository.findFirstByStationAndStatusAndDeletedFalseOrderByStartAtDesc(station, StoppageStatus.ACTIVE))
                .thenReturn(Optional.empty());
        when(currentUserService.requireUser(principal)).thenReturn(user);
        StoppageService service = new StoppageService(
                repository,
                stationScopeService,
                currentUserService,
                mock(ShiftCalculator.class),
                mock(AuditService.class),
                mock(NotificationDispatcher.class),
                mock(NotificationRecipientResolver.class),
                fixedClock()
        );

        assertThatThrownBy(() -> service.start(request, principal))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Descreva o motivo da parada");
        verify(repository, org.mockito.Mockito.never()).save(any(Stoppage.class));
    }

    @Test
    void startDropsFreeTextWhenReasonIsNotOutros() {
        StoppageRepository repository = mock(StoppageRepository.class);
        StationScopeService stationScopeService = mock(StationScopeService.class);
        CurrentUserService currentUserService = mock(CurrentUserService.class);
        Station station = station(UUID.randomUUID(), "ITE A");
        AppUser user = user(UserRole.OPERATOR, station);
        UserPrincipal principal = UserPrincipal.from(user);
        StoppageStartRequest request = new StoppageStartRequest(
                station.getId(),
                StoppageReason.ENTUPIMENTO_DE_BOMBAS,
                "texto que deve ser descartado",
                CURRENT_TIME
        );
        when(stationScopeService.requireWritableStation(station.getId(), principal)).thenReturn(station);
        when(repository.findFirstByStationAndStatusAndDeletedFalseOrderByStartAtDesc(station, StoppageStatus.ACTIVE))
                .thenReturn(Optional.empty());
        when(currentUserService.requireUser(principal)).thenReturn(user);
        when(repository.save(any(Stoppage.class))).thenAnswer(invocation -> invocation.getArgument(0));
        StoppageService service = new StoppageService(
                repository,
                stationScopeService,
                currentUserService,
                mock(ShiftCalculator.class),
                mock(AuditService.class),
                mock(NotificationDispatcher.class),
                mock(NotificationRecipientResolver.class),
                fixedClock()
        );

        StoppageResponse response = service.start(request, principal);

        assertThat(response.reason()).isEqualTo(StoppageReason.ENTUPIMENTO_DE_BOMBAS);
        assertThat(response.reasonDetail()).isNull();
    }

    @Test
    void startKeepsTrimmedFreeTextForOutros() {
        StoppageRepository repository = mock(StoppageRepository.class);
        StationScopeService stationScopeService = mock(StationScopeService.class);
        CurrentUserService currentUserService = mock(CurrentUserService.class);
        Station station = station(UUID.randomUUID(), "ITE A");
        AppUser user = user(UserRole.OPERATOR, station);
        UserPrincipal principal = UserPrincipal.from(user);
        StoppageStartRequest request = new StoppageStartRequest(
                station.getId(),
                StoppageReason.OUTROS,
                "  Queda de energia  ",
                CURRENT_TIME
        );
        when(stationScopeService.requireWritableStation(station.getId(), principal)).thenReturn(station);
        when(repository.findFirstByStationAndStatusAndDeletedFalseOrderByStartAtDesc(station, StoppageStatus.ACTIVE))
                .thenReturn(Optional.empty());
        when(currentUserService.requireUser(principal)).thenReturn(user);
        when(repository.save(any(Stoppage.class))).thenAnswer(invocation -> invocation.getArgument(0));
        StoppageService service = new StoppageService(
                repository,
                stationScopeService,
                currentUserService,
                mock(ShiftCalculator.class),
                mock(AuditService.class),
                mock(NotificationDispatcher.class),
                mock(NotificationRecipientResolver.class),
                fixedClock()
        );

        assertThat(service.start(request, principal).reasonDetail()).isEqualTo("Queda de energia");
    }

    @Test
    void startRejectsFutureStartAtBeforeResolvingScope() {
        StoppageRepository repository = mock(StoppageRepository.class);
        StationScopeService stationScopeService = mock(StationScopeService.class);
        StoppageService service = service(repository, stationScopeService, fixedClock());
        AppUser user = user(UserRole.OPERATOR, station(UUID.randomUUID(), "ITE A"));
        UserPrincipal principal = UserPrincipal.from(user);
        StoppageStartRequest request = new StoppageStartRequest(
                user.getStation().getId(),
                StoppageReason.FALTA_DE_AGUA,
                null,
                CURRENT_TIME.plusSeconds(1)
        );

        assertThatThrownBy(() -> service.start(request, principal))
                .isInstanceOf(BusinessException.class)
                .hasMessage(FUTURE_TIME_MESSAGE);

        verifyNoInteractions(repository, stationScopeService);
    }

    @Test
    void startAllowsStartAtEqualToCurrentTime() {
        StoppageRepository repository = mock(StoppageRepository.class);
        StationScopeService stationScopeService = mock(StationScopeService.class);
        CurrentUserService currentUserService = mock(CurrentUserService.class);
        AuditService auditService = mock(AuditService.class);
        Station station = station(UUID.randomUUID(), "ITE A");
        AppUser user = user(UserRole.OPERATOR, station);
        UserPrincipal principal = UserPrincipal.from(user);
        StoppageStartRequest request = new StoppageStartRequest(
                station.getId(),
                StoppageReason.FALTA_DE_AGUA,
                null,
                CURRENT_TIME
        );
        when(stationScopeService.requireWritableStation(station.getId(), principal)).thenReturn(station);
        when(repository.findFirstByStationAndStatusAndDeletedFalseOrderByStartAtDesc(
                station,
                StoppageStatus.ACTIVE
        )).thenReturn(Optional.empty());
        when(currentUserService.requireUser(principal)).thenReturn(user);
        when(repository.save(org.mockito.ArgumentMatchers.any(Stoppage.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        StoppageService service = new StoppageService(
                repository,
                stationScopeService,
                currentUserService,
                mock(ShiftCalculator.class),
                auditService,
                mock(NotificationDispatcher.class),
                mock(NotificationRecipientResolver.class),
                fixedClock()
        );

        StoppageResponse response = service.start(request, principal);

        assertThat(response.startAt()).isEqualTo(CURRENT_TIME);
    }

    @Test
    void startTranslatesActiveStoppageConstraintViolation() {
        StoppageRepository repository = mock(StoppageRepository.class);
        StationScopeService stationScopeService = mock(StationScopeService.class);
        CurrentUserService currentUserService = mock(CurrentUserService.class);
        Station station = station(UUID.randomUUID(), "ITE A");
        AppUser user = user(UserRole.OPERATOR, station);
        UserPrincipal principal = UserPrincipal.from(user);
        StoppageStartRequest request = new StoppageStartRequest(station.getId(), StoppageReason.FALTA_DE_AGUA, null, CURRENT_TIME);
        when(stationScopeService.requireWritableStation(station.getId(), principal)).thenReturn(station);
        when(repository.findFirstByStationAndStatusAndDeletedFalseOrderByStartAtDesc(station, StoppageStatus.ACTIVE)).thenReturn(Optional.empty());
        when(currentUserService.requireUser(principal)).thenReturn(user);
        when(repository.save(any(Stoppage.class))).thenThrow(new DataIntegrityViolationException("duplicate"));
        StoppageService service = new StoppageService(repository, stationScopeService, currentUserService, mock(ShiftCalculator.class), mock(AuditService.class), mock(NotificationDispatcher.class), mock(NotificationRecipientResolver.class), fixedClock());

        assertThatThrownBy(() -> service.start(request, principal))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Station already has an active stoppage");
    }

    @Test
    void startAllowsMultipleFinishedStoppagesForStation() {
        StoppageRepository repository = mock(StoppageRepository.class);
        StationScopeService stationScopeService = mock(StationScopeService.class);
        CurrentUserService currentUserService = mock(CurrentUserService.class);
        Station station = station(UUID.randomUUID(), "ITE A");
        AppUser user = user(UserRole.OPERATOR, station);
        UserPrincipal principal = UserPrincipal.from(user);
        StoppageStartRequest request = new StoppageStartRequest(station.getId(), StoppageReason.FALTA_DE_AGUA, null, CURRENT_TIME);
        when(stationScopeService.requireWritableStation(station.getId(), principal)).thenReturn(station);
        when(repository.findFirstByStationAndStatusAndDeletedFalseOrderByStartAtDesc(station, StoppageStatus.ACTIVE)).thenReturn(Optional.empty());
        when(currentUserService.requireUser(principal)).thenReturn(user);
        when(repository.save(any(Stoppage.class))).thenAnswer(invocation -> invocation.getArgument(0));
        StoppageService service = new StoppageService(repository, stationScopeService, currentUserService, mock(ShiftCalculator.class), mock(AuditService.class), mock(NotificationDispatcher.class), mock(NotificationRecipientResolver.class), fixedClock());

        service.start(request, principal);
        service.start(request, principal);

        verify(repository, org.mockito.Mockito.times(2)).save(any(Stoppage.class));
    }

    @Test
    void startDispatchesStoppageAlertToActiveSupervisors() {
        StoppageRepository repository = mock(StoppageRepository.class);
        StationScopeService stationScopeService = mock(StationScopeService.class);
        CurrentUserService currentUserService = mock(CurrentUserService.class);
        NotificationDispatcher dispatcher = mock(NotificationDispatcher.class);
        NotificationRecipientResolver recipientResolver = mock(NotificationRecipientResolver.class);
        Station station = station(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"), "ITE A");
        AppUser operator = user(UserRole.OPERATOR, station);
        AppUser supervisor = user(UserRole.SUPERVISOR, null);
        UserPrincipal principal = UserPrincipal.from(operator);
        Instant startAt = Instant.parse("2026-07-01T08:30:00Z");
        StoppageStartRequest request = new StoppageStartRequest(
                station.getId(),
                StoppageReason.FALTA_DE_AGUA,
                null,
                startAt
        );
        when(stationScopeService.requireWritableStation(station.getId(), principal)).thenReturn(station);
        when(repository.findFirstByStationAndStatusAndDeletedFalseOrderByStartAtDesc(
                station,
                StoppageStatus.ACTIVE
        )).thenReturn(Optional.empty());
        when(currentUserService.requireUser(principal)).thenReturn(operator);
        when(repository.save(any(Stoppage.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(recipientResolver.stoppageAlertRecipients(station)).thenReturn(List.of(supervisor));
        StoppageService service = new StoppageService(
                repository,
                stationScopeService,
                currentUserService,
                mock(ShiftCalculator.class),
                mock(AuditService.class),
                dispatcher,
                recipientResolver,
                fixedClock()
        );

        service.start(request, principal);

        ArgumentCaptor<NotificationCommand> captor = ArgumentCaptor.forClass(NotificationCommand.class);
        verify(dispatcher).dispatch(captor.capture());
        NotificationCommand command = captor.getValue();
        assertThat(command.type()).isEqualTo(NotificationType.STOPPAGE_ALERT);
        assertThat(command.recipients()).containsExactly(supervisor);
        assertThat(command.station()).isEqualTo(station);
        assertThat(command.title()).isEqualTo("PARADA ETE ITE A");
        assertThat(command.message()).isEqualTo("Motivo: Falta de água, Início: 05:30");
        assertThat(command.targetUrl()).isEqualTo("/app/dashboard?station=aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        assertThat(command.dedupeKey()).isNull();
        assertThat(command.scheduledAt()).isEqualTo(startAt);
    }

    @Test
    void startRejectsWhenIntervalOverlapsExistingStoppage() {
        StoppageRepository repository = mock(StoppageRepository.class);
        StationScopeService stationScopeService = mock(StationScopeService.class);
        Station station = station(UUID.randomUUID(), "ITE A");
        AppUser user = user(UserRole.OPERATOR, station);
        UserPrincipal principal = UserPrincipal.from(user);
        StoppageStartRequest request = new StoppageStartRequest(
                station.getId(),
                StoppageReason.FALTA_DE_AGUA,
                null,
                CURRENT_TIME.minusSeconds(600)
        );
        when(stationScopeService.requireWritableStation(station.getId(), principal)).thenReturn(station);
        when(repository.findOverlapConflicts(eq(station.getId()), any(), any(), any(), isNull()))
                .thenReturn(List.of(stoppage(
                        UUID.randomUUID(),
                        station,
                        StoppageReason.FALTA_DE_AGUA,
                        CURRENT_TIME.minusSeconds(1200),
                        CURRENT_TIME.minusSeconds(300),
                        user
                )));
        StoppageService service = service(repository, stationScopeService, fixedClock());

        assertThatThrownBy(() -> service.start(request, principal))
                .isInstanceOf(BusinessException.class)
                .hasMessage(OVERLAP_MESSAGE);
    }

    @Test
    void finishRejectsWhenFinalIntervalOverlapsAnotherStoppage() {
        UUID stoppageId = UUID.randomUUID();
        Station station = station(UUID.randomUUID(), "ITE A");
        AppUser user = user(UserRole.OPERATOR, station);
        UserPrincipal principal = UserPrincipal.from(user);
        Stoppage stoppage = stoppage(
                stoppageId,
                station,
                StoppageReason.FALTA_DE_AGUA,
                CURRENT_TIME.minusSeconds(3600),
                null,
                user
        );
        StoppageRepository repository = mock(StoppageRepository.class);
        StationScopeService stationScopeService = mock(StationScopeService.class);
        when(repository.findByIdAndDeletedFalse(stoppageId)).thenReturn(Optional.of(stoppage));
        when(repository.findOverlapConflicts(eq(station.getId()), any(), any(), any(), eq(stoppageId)))
                .thenReturn(List.of(stoppage(
                        UUID.randomUUID(),
                        station,
                        StoppageReason.OUTROS,
                        CURRENT_TIME.minusSeconds(1800),
                        null,
                        user
                )));
        StoppageService service = new StoppageService(
                repository,
                stationScopeService,
                mock(CurrentUserService.class),
                mock(ShiftCalculator.class),
                mock(AuditService.class),
                mock(NotificationDispatcher.class),
                mock(NotificationRecipientResolver.class),
                fixedClock()
        );

        assertThatThrownBy(() -> service.finish(stoppageId, new StoppageFinishRequest(CURRENT_TIME), principal))
                .isInstanceOf(BusinessException.class)
                .hasMessage(OVERLAP_MESSAGE);
    }

    @Test
    void manualUpdateRejectsWhenIntervalOverlapsAnotherStoppage() {
        UUID stoppageId = UUID.randomUUID();
        Station station = station(UUID.randomUUID(), "ITE A");
        AppUser user = user(UserRole.OPERATOR, station);
        UserPrincipal principal = UserPrincipal.from(user);
        Stoppage stoppage = stoppage(
                stoppageId,
                station,
                StoppageReason.FALTA_DE_AGUA,
                CURRENT_TIME.minusSeconds(3600),
                null,
                user
        );
        StoppageRepository repository = mock(StoppageRepository.class);
        StationScopeService stationScopeService = mock(StationScopeService.class);
        when(repository.findByIdAndDeletedFalse(stoppageId)).thenReturn(Optional.of(stoppage));
        when(repository.findOverlapConflicts(eq(station.getId()), any(), any(), any(), eq(stoppageId)))
                .thenReturn(List.of(stoppage(
                        UUID.randomUUID(),
                        station,
                        StoppageReason.OUTROS,
                        CURRENT_TIME.minusSeconds(2400),
                        CURRENT_TIME.minusSeconds(600),
                        user
                )));
        StoppageService service = new StoppageService(
                repository,
                stationScopeService,
                mock(CurrentUserService.class),
                mock(ShiftCalculator.class),
                mock(AuditService.class),
                mock(NotificationDispatcher.class),
                mock(NotificationRecipientResolver.class),
                fixedClock()
        );
        StoppageUpdateRequest request = new StoppageUpdateRequest(
                StoppageReason.OUTROS,
                "Motivo livre da parada",
                CURRENT_TIME.minusSeconds(3000),
                CURRENT_TIME.minusSeconds(1200),
                "Corrected stoppage data"
        );

        assertThatThrownBy(() -> service.update(stoppageId, request, principal))
                .isInstanceOf(BusinessException.class)
                .hasMessage(OVERLAP_MESSAGE);
    }

    @Test
    void finishRejectsFutureEndAtBeforeLoadingStoppage() {
        StoppageRepository repository = mock(StoppageRepository.class);
        StationScopeService stationScopeService = mock(StationScopeService.class);
        StoppageService service = service(repository, stationScopeService, fixedClock());
        UserPrincipal principal = UserPrincipal.from(user(UserRole.ADMIN, null));

        assertThatThrownBy(() -> service.finish(
                UUID.randomUUID(),
                new StoppageFinishRequest(CURRENT_TIME.plusSeconds(1)),
                principal
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessage(FUTURE_TIME_MESSAGE);

        verifyNoInteractions(repository, stationScopeService);
    }

    @Test
    void finishAllowsEndAtEqualToCurrentTime() {
        UUID stoppageId = UUID.randomUUID();
        Station station = station(UUID.randomUUID(), "ITE A");
        AppUser user = user(UserRole.OPERATOR, station);
        UserPrincipal principal = UserPrincipal.from(user);
        Stoppage stoppage = stoppage(
                stoppageId,
                station,
                StoppageReason.FALTA_DE_AGUA,
                CURRENT_TIME.minusSeconds(60),
                null,
                user
        );
        StoppageService service = service(stoppageId, stoppage, user, principal, fixedClock());

        StoppageResponse response = service.finish(stoppageId, new StoppageFinishRequest(CURRENT_TIME), principal);

        assertThat(response.endAt()).isEqualTo(CURRENT_TIME);
        assertThat(response.status()).isEqualTo(StoppageStatus.FINISHED);
        assertNoNotificationDispatch(service);
    }

    @Test
    void manualUpdateRejectsFutureStartAtBeforeLoadingStoppage() {
        StoppageRepository repository = mock(StoppageRepository.class);
        StationScopeService stationScopeService = mock(StationScopeService.class);
        StoppageService service = service(repository, stationScopeService, fixedClock());
        UserPrincipal principal = UserPrincipal.from(user(UserRole.ADMIN, null));
        StoppageUpdateRequest request = new StoppageUpdateRequest(
                StoppageReason.OUTROS,
                "Motivo livre da parada",
                CURRENT_TIME.plusSeconds(1),
                null,
                "Corrected stoppage data"
        );

        assertThatThrownBy(() -> service.update(UUID.randomUUID(), request, principal))
                .isInstanceOf(BusinessException.class)
                .hasMessage(FUTURE_TIME_MESSAGE);

        verifyNoInteractions(repository, stationScopeService);
    }

    @Test
    void manualUpdateRejectsFutureEndAtBeforeLoadingStoppage() {
        StoppageRepository repository = mock(StoppageRepository.class);
        StationScopeService stationScopeService = mock(StationScopeService.class);
        StoppageService service = service(repository, stationScopeService, fixedClock());
        UserPrincipal principal = UserPrincipal.from(user(UserRole.ADMIN, null));
        StoppageUpdateRequest request = new StoppageUpdateRequest(
                StoppageReason.OUTROS,
                "Motivo livre da parada",
                CURRENT_TIME.minusSeconds(60),
                CURRENT_TIME.plusSeconds(1),
                "Corrected stoppage data"
        );

        assertThatThrownBy(() -> service.update(UUID.randomUUID(), request, principal))
                .isInstanceOf(BusinessException.class)
                .hasMessage(FUTURE_TIME_MESSAGE);

        verifyNoInteractions(repository, stationScopeService);
    }

    @Test
    void manualUpdateGeneratesUpdatedAuditWithChangedFieldsOnly() {
        UUID stoppageId = UUID.randomUUID();
        Station station = station(UUID.randomUUID(), "ITE A");
        AppUser user = user(UserRole.OPERATOR, station);
        UserPrincipal principal = UserPrincipal.from(user);
        Stoppage stoppage = stoppage(
                stoppageId,
                station,
                StoppageReason.FALTA_DE_AGUA,
                Instant.parse("2026-07-01T10:00:00Z"),
                null,
                user
        );
        StoppageService service = service(stoppageId, stoppage, user, principal);
        StoppageUpdateRequest request = new StoppageUpdateRequest(
                StoppageReason.OUTROS,
                "Motivo livre da parada",
                Instant.parse("2026-07-01T11:00:00Z"),
                Instant.parse("2026-07-01T12:00:00Z"),
                "Corrected stoppage data"
        );

        StoppageResponse response = service.update(stoppageId, request, principal);

        assertThat(response.reason()).isEqualTo(StoppageReason.OUTROS);
        assertThat(response.status()).isEqualTo(StoppageStatus.FINISHED);
        assertNoNotificationDispatch(service);

        AuditService auditService = (AuditService) ReflectionTestUtils.getField(service, "auditService");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AuditFieldChangeDraft>> changesCaptor = ArgumentCaptor.forClass(List.class);
        verify(auditService).recordUpdate(
                eq(AuditRecordType.STOPPAGE),
                eq(stoppageId),
                eq(station),
                eq(user),
                eq("Corrected stoppage data"),
                changesCaptor.capture()
        );
        assertThat(changesCaptor.getValue())
                .extracting(AuditFieldChangeDraft::name)
                .containsExactly("reason", "reasonDetail", "startAt", "endAt");
    }

    @Test
    void deleteSoftDeletesAndGeneratesDeletedAudit() {
        UUID stoppageId = UUID.randomUUID();
        Station station = station(UUID.randomUUID(), "ITE A");
        AppUser user = user(UserRole.OPERATOR, station);
        UserPrincipal principal = UserPrincipal.from(user);
        Stoppage stoppage = stoppage(
                stoppageId,
                station,
                StoppageReason.FALTA_DE_AGUA,
                Instant.parse("2026-07-01T10:00:00Z"),
                null,
                user
        );
        StoppageService service = service(stoppageId, stoppage, user, principal);

        service.delete(stoppageId, new AuditDescriptionRequest("Removing duplicated stoppage"), principal);

        assertThat(stoppage.isDeleted()).isTrue();
        assertThat(stoppage.getDeletedBy()).isEqualTo(user);
        assertThat(stoppage.getDeletedAt()).isNotNull();
        assertNoNotificationDispatch(service);

        AuditService auditService = (AuditService) ReflectionTestUtils.getField(service, "auditService");
        verify(auditService).recordDelete(
                AuditRecordType.STOPPAGE,
                stoppageId,
                station,
                user,
                "Removing duplicated stoppage"
        );
    }

    @Test
    void manualUpdateWithoutChangesFailsWithoutAuditLog() {
        UUID stoppageId = UUID.randomUUID();
        Station station = station(UUID.randomUUID(), "ITE A");
        AppUser user = user(UserRole.OPERATOR, station);
        UserPrincipal principal = UserPrincipal.from(user);
        Instant startAt = Instant.parse("2026-07-01T10:00:00Z");
        Stoppage stoppage = stoppage(
                stoppageId,
                station,
                StoppageReason.FALTA_DE_AGUA,
                startAt,
                null,
                user
        );
        StoppageService service = service(stoppageId, stoppage, user, principal);
        StoppageUpdateRequest request = new StoppageUpdateRequest(
                StoppageReason.FALTA_DE_AGUA,
                null,
                startAt,
                null,
                "Trying to save without changes"
        );

        assertThatThrownBy(() -> service.update(stoppageId, request, principal))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Update must change at least one field");

        AuditService auditService = (AuditService) ReflectionTestUtils.getField(service, "auditService");
        verifyNoInteractions(auditService);
    }

    @Test
    void deletedDetailReturnsCurrentDataAndHistory() {
        UUID stoppageId = UUID.randomUUID();
        Station station = station(UUID.randomUUID(), "ITE A");
        AppUser user = user(UserRole.OPERATOR, station);
        UserPrincipal principal = UserPrincipal.from(user);
        Stoppage stoppage = stoppage(
                stoppageId,
                station,
                StoppageReason.FALTA_DE_AGUA,
                Instant.parse("2026-07-01T10:00:00Z"),
                null,
                user
        );
        stoppage.softDelete(user);

        StoppageRepository repository = mock(StoppageRepository.class);
        StationScopeService stationScopeService = mock(StationScopeService.class);
        CurrentUserService currentUserService = mock(CurrentUserService.class);
        AuditService auditService = mock(AuditService.class);
        ShiftCalculator shiftCalculator = mock(ShiftCalculator.class);
        List<AuditEventResponse> history = List.of(new AuditEventResponse(
                UUID.randomUUID(),
                AuditRecordType.STOPPAGE,
                AuditAction.DELETED,
                stoppageId,
                station.getId(),
                station.getName(),
                user.getId(),
                user.getName(),
                Instant.parse("2026-07-01T12:00:00Z"),
                "Removing duplicated stoppage",
                List.of()
        ));
        when(repository.findById(stoppageId)).thenReturn(Optional.of(stoppage));
        when(auditService.history(AuditRecordType.STOPPAGE, stoppageId, principal)).thenReturn(history);
        StoppageService service = new StoppageService(
                repository,
                stationScopeService,
                currentUserService,
                shiftCalculator,
                auditService,
                mock(NotificationDispatcher.class),
                mock(NotificationRecipientResolver.class)
        );

        StoppageDetailResponse response = service.detail(stoppageId, principal);

        assertThat(response.deleted()).isTrue();
        assertThat(response.edited()).isFalse();
        assertThat(response.stoppage().id()).isEqualTo(stoppageId);
        assertThat(response.changes()).containsExactlyElementsOf(history);
        verify(stationScopeService).requireReadableStation(station, principal);
    }

    @Test
    void operatorCannotReadDeletedOldOpenStoppageDetail() {
        UUID stoppageId = UUID.randomUUID();
        Station station = station(UUID.randomUUID(), "ITE A");
        AppUser user = user(UserRole.OPERATOR, station);
        UserPrincipal principal = UserPrincipal.from(user);
        Stoppage stoppage = stoppage(
                stoppageId,
                station,
                StoppageReason.FALTA_DE_AGUA,
                CURRENT_TIME.minusSeconds(30 * 60 * 60),
                null,
                user
        );
        stoppage.softDelete(user);

        StoppageRepository repository = mock(StoppageRepository.class);
        StationScopeService stationScopeService = mock(StationScopeService.class);
        AuditService auditService = mock(AuditService.class);
        when(repository.findById(stoppageId)).thenReturn(Optional.of(stoppage));
        StoppageService service = new StoppageService(
                repository,
                stationScopeService,
                mock(CurrentUserService.class),
                mock(ShiftCalculator.class),
                auditService,
                mock(NotificationDispatcher.class),
                mock(NotificationRecipientResolver.class),
                fixedClock(),
                OperatorWindowPolicy.defaultWindow(fixedClock())
        );

        assertThatThrownBy(() -> service.detail(stoppageId, principal))
                .isInstanceOf(AccessDeniedException.class);

        verify(stationScopeService).requireReadableStation(station, principal);
        verifyNoInteractions(auditService);
    }

    @Test
    void operatorCannotReadShiftSlicesForFinishedStoppageOutsideRecordWindow() {
        UUID stoppageId = UUID.randomUUID();
        Station station = station(UUID.randomUUID(), "ITE A");
        AppUser user = user(UserRole.OPERATOR, station);
        UserPrincipal principal = UserPrincipal.from(user);
        Stoppage stoppage = stoppage(
                stoppageId,
                station,
                StoppageReason.FALTA_DE_AGUA,
                CURRENT_TIME.minusSeconds(30 * 60 * 60),
                CURRENT_TIME.minusSeconds(29 * 60 * 60),
                user
        );
        StoppageRepository repository = mock(StoppageRepository.class);
        ShiftCalculator shiftCalculator = mock(ShiftCalculator.class);
        when(repository.findByIdAndDeletedFalse(stoppageId)).thenReturn(Optional.of(stoppage));
        StoppageService service = new StoppageService(
                repository,
                mock(StationScopeService.class),
                mock(CurrentUserService.class),
                shiftCalculator,
                mock(AuditService.class),
                mock(NotificationDispatcher.class),
                mock(NotificationRecipientResolver.class),
                fixedClock(),
                OperatorWindowPolicy.defaultWindow(fixedClock())
        );

        assertThatThrownBy(() -> service.shiftSlices(stoppageId, principal))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(shiftCalculator);
    }

    @Test
    void listAcceptsOnlyEndAt() {
        StoppageRepository repository = mock(StoppageRepository.class);
        StationScopeService stationScopeService = mock(StationScopeService.class);
        StoppageService service = service(repository, stationScopeService);
        UserPrincipal principal = UserPrincipal.from(user(UserRole.ADMIN, null));
        Station station = station(UUID.randomUUID(), "ITE A");
        Instant endAt = Instant.parse("2026-07-02T00:00:00Z");
        when(stationScopeService.resolveReadableStations(null, false, principal)).thenReturn(List.of(station));
        when(repository.findForList(List.of(station), null, null, endAt, false)).thenReturn(List.of());

        assertThat(service.list(
                null,
                false,
                null,
                null,
                endAt,
                false,
                principal
        )).isEmpty();

        verify(repository).findForList(List.of(station), null, null, endAt, false);
    }

    @Test
    void listRejectsEndAtBeforeStartAtBeforeResolvingScope() {
        StoppageRepository repository = mock(StoppageRepository.class);
        StationScopeService stationScopeService = mock(StationScopeService.class);
        StoppageService service = service(repository, stationScopeService);
        UserPrincipal principal = UserPrincipal.from(user(UserRole.ADMIN, null));

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
    void listUsesResolvedReadableStationsWhenOperatorRequestsConsolidatedScope() {
        UUID stoppageId = UUID.randomUUID();
        Instant startAt = Instant.parse("2026-07-01T00:00:00Z");
        Instant endAt = Instant.parse("2026-07-02T00:00:00Z");
        Station station = station(UUID.randomUUID(), "ITE A");
        AppUser user = user(UserRole.OPERATOR, station);
        UserPrincipal principal = UserPrincipal.from(user);
        Stoppage stoppage = stoppage(
                stoppageId,
                station,
                StoppageReason.FALTA_DE_AGUA,
                Instant.parse("2026-07-01T10:00:00Z"),
                null,
                user
        );
        StoppageRepository repository = mock(StoppageRepository.class);
        StationScopeService stationScopeService = mock(StationScopeService.class);
        when(stationScopeService.resolveReadableStations(null, true, principal)).thenReturn(List.of(station));
        when(repository.findForList(List.of(station), StoppageStatus.ACTIVE, startAt, endAt, false))
                .thenReturn(List.of(stoppage));
        StoppageService service = service(repository, stationScopeService);

        List<StoppageResponse> response = service.list(
                null,
                true,
                StoppageStatus.ACTIVE,
                startAt,
                endAt,
                false,
                principal
        );

        assertThat(response)
                .extracting(StoppageResponse::id)
                .containsExactly(stoppageId);
        verify(stationScopeService).resolveReadableStations(null, true, principal);
        verify(repository).findForList(List.of(station), StoppageStatus.ACTIVE, startAt, endAt, false);
    }

    @Test
    void listPassesStationStatusPeriodAndDeletedFiltersToRepository() {
        UUID stationId = UUID.randomUUID();
        Instant startAt = Instant.parse("2026-07-01T00:00:00Z");
        Instant endAt = Instant.parse("2026-07-02T00:00:00Z");
        Station station = station(stationId, "ITE A");
        AppUser user = user(UserRole.ADMIN, null);
        UserPrincipal principal = UserPrincipal.from(user);
        StoppageRepository repository = mock(StoppageRepository.class);
        StationScopeService stationScopeService = mock(StationScopeService.class);
        when(stationScopeService.resolveReadableStations(stationId, false, principal)).thenReturn(List.of(station));
        when(repository.findForList(List.of(station), StoppageStatus.FINISHED, startAt, endAt, true))
                .thenReturn(List.of());
        StoppageService service = service(repository, stationScopeService);

        List<StoppageResponse> response = service.list(
                stationId,
                false,
                StoppageStatus.FINISHED,
                startAt,
                endAt,
                true,
                principal
        );

        assertThat(response).isEmpty();
        verify(stationScopeService).resolveReadableStations(stationId, false, principal);
        verify(repository).findForList(List.of(station), StoppageStatus.FINISHED, startAt, endAt, true);
    }

    @Test
    void operatorListUsesWindowQueryThatKeepsOldOpenStoppages() {
        Instant requestedStartAt = Instant.parse("2026-06-01T00:00:00Z");
        Instant effectiveStartAt = CURRENT_TIME.minusSeconds(24 * 60 * 60);
        Instant endAt = Instant.parse("2026-07-02T00:00:00Z");
        Station station = station(UUID.randomUUID(), "ITE A");
        AppUser user = user(UserRole.OPERATOR, station);
        UserPrincipal principal = UserPrincipal.from(user);
        StoppageRepository repository = mock(StoppageRepository.class);
        StationScopeService stationScopeService = mock(StationScopeService.class);
        when(stationScopeService.resolveReadableStations(station.getId(), false, principal)).thenReturn(List.of(station));
        when(repository.findForOperatorList(List.of(station), null, effectiveStartAt, endAt, false)).thenReturn(List.of());
        StoppageService service = new StoppageService(
                repository,
                stationScopeService,
                mock(CurrentUserService.class),
                mock(ShiftCalculator.class),
                mock(AuditService.class),
                mock(NotificationDispatcher.class),
                mock(NotificationRecipientResolver.class),
                fixedClock(),
                OperatorWindowPolicy.defaultWindow(fixedClock())
        );

        service.list(station.getId(), false, null, requestedStartAt, endAt, false, principal);

        verify(repository).findForOperatorList(List.of(station), null, effectiveStartAt, endAt, false);
    }

    @Test
    void operatorCanFinishOldOpenStoppageButCannotUpdateIt() {
        UUID stoppageId = UUID.randomUUID();
        Station station = station(UUID.randomUUID(), "ITE A");
        AppUser user = user(UserRole.OPERATOR, station);
        UserPrincipal principal = UserPrincipal.from(user);
        Stoppage stoppage = stoppage(
                stoppageId,
                station,
                StoppageReason.FALTA_DE_AGUA,
                CURRENT_TIME.minusSeconds(30 * 60 * 60),
                null,
                user
        );
        StoppageRepository repository = mock(StoppageRepository.class);
        StationScopeService stationScopeService = mock(StationScopeService.class);
        when(repository.findByIdAndDeletedFalse(stoppageId)).thenReturn(Optional.of(stoppage));
        when(repository.findOverlapConflicts(eq(station.getId()), any(), any(), any(), eq(stoppageId)))
                .thenReturn(List.of());
        StoppageService service = new StoppageService(
                repository,
                stationScopeService,
                mock(CurrentUserService.class),
                mock(ShiftCalculator.class),
                mock(AuditService.class),
                mock(NotificationDispatcher.class),
                mock(NotificationRecipientResolver.class),
                fixedClock(),
                OperatorWindowPolicy.defaultWindow(fixedClock())
        );

        StoppageResponse response = service.finish(stoppageId, new StoppageFinishRequest(CURRENT_TIME), principal);

        assertThat(response.status()).isEqualTo(StoppageStatus.FINISHED);
        StoppageUpdateRequest request = new StoppageUpdateRequest(
                StoppageReason.OUTROS,
                "Motivo livre da parada",
                CURRENT_TIME.minusSeconds(30 * 60 * 60),
                CURRENT_TIME,
                "Trying to edit old stoppage"
        );
        assertThatThrownBy(() -> service.update(stoppageId, request, principal))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void operatorListsAndFinishesOpenStoppageCreatedByAnotherOperatorOfTheSameStation() {
        UUID stoppageId = UUID.randomUUID();
        Instant requestedStartAt = Instant.parse("2026-06-01T00:00:00Z");
        Instant effectiveStartAt = CURRENT_TIME.minusSeconds(24 * 60 * 60);
        Instant endAt = Instant.parse("2026-07-02T00:00:00Z");
        Station station = station(UUID.randomUUID(), "ITE A");
        AppUser operatorA = user(UserRole.OPERATOR, station);
        AppUser operatorB = user(UserRole.OPERATOR, station);
        UserPrincipal principalB = UserPrincipal.from(operatorB);
        Stoppage stoppageOfA = stoppage(
                stoppageId,
                station,
                StoppageReason.FALTA_DE_AGUA,
                CURRENT_TIME.minusSeconds(30 * 60 * 60),
                null,
                operatorA
        );
        StoppageRepository repository = mock(StoppageRepository.class);
        StationScopeService stationScopeService = mock(StationScopeService.class);
        when(stationScopeService.resolveReadableStations(station.getId(), false, principalB))
                .thenReturn(List.of(station));
        when(repository.findForOperatorList(List.of(station), null, effectiveStartAt, endAt, false))
                .thenReturn(List.of(stoppageOfA));
        when(repository.findByIdAndDeletedFalse(stoppageId)).thenReturn(Optional.of(stoppageOfA));
        when(repository.findOverlapConflicts(eq(station.getId()), any(), any(), any(), eq(stoppageId)))
                .thenReturn(List.of());
        StoppageService service = new StoppageService(
                repository,
                stationScopeService,
                mock(CurrentUserService.class),
                mock(ShiftCalculator.class),
                mock(AuditService.class),
                mock(NotificationDispatcher.class),
                mock(NotificationRecipientResolver.class),
                fixedClock(),
                OperatorWindowPolicy.defaultWindow(fixedClock())
        );

        List<StoppageResponse> listed = service.list(
                station.getId(),
                false,
                null,
                requestedStartAt,
                endAt,
                false,
                principalB
        );

        assertThat(listed).extracting(StoppageResponse::id).containsExactly(stoppageId);
        assertThat(listed).extracting(StoppageResponse::createdById).containsExactly(operatorA.getId());

        StoppageResponse finished = service.finish(
                stoppageId,
                new StoppageFinishRequest(CURRENT_TIME),
                principalB
        );

        assertThat(finished.status()).isEqualTo(StoppageStatus.FINISHED);
        assertThat(finished.endAt()).isEqualTo(CURRENT_TIME);
        assertThat(finished.createdById()).isEqualTo(operatorA.getId());
    }

    @Test
    void operatorCannotDeleteStoppageOutsideRecordWindow() {
        UUID stoppageId = UUID.randomUUID();
        Station station = station(UUID.randomUUID(), "ITE A");
        AppUser user = user(UserRole.OPERATOR, station);
        UserPrincipal principal = UserPrincipal.from(user);
        Stoppage stoppage = stoppage(
                stoppageId,
                station,
                StoppageReason.FALTA_DE_AGUA,
                CURRENT_TIME.minusSeconds(30 * 60 * 60),
                CURRENT_TIME.minusSeconds(29 * 60 * 60),
                user
        );
        StoppageRepository repository = mock(StoppageRepository.class);
        AuditService auditService = mock(AuditService.class);
        when(repository.findByIdAndDeletedFalse(stoppageId)).thenReturn(Optional.of(stoppage));
        StoppageService service = new StoppageService(
                repository,
                mock(StationScopeService.class),
                mock(CurrentUserService.class),
                mock(ShiftCalculator.class),
                auditService,
                mock(NotificationDispatcher.class),
                mock(NotificationRecipientResolver.class),
                fixedClock(),
                OperatorWindowPolicy.defaultWindow(fixedClock())
        );

        assertThatThrownBy(() -> service.delete(
                stoppageId,
                new AuditDescriptionRequest("Trying to delete old stoppage"),
                principal
        ))
                .isInstanceOf(AccessDeniedException.class);

        assertThat(stoppage.isDeleted()).isFalse();
        verifyNoInteractions(auditService);
    }

    @Test
    void adminIsExemptFromOperatorWindowOnUpdateAndDelete() {
        UUID updatedId = UUID.randomUUID();
        UUID deletedId = UUID.randomUUID();
        Station station = station(UUID.randomUUID(), "ITE A");
        AppUser operator = user(UserRole.OPERATOR, station);
        AppUser admin = user(UserRole.ADMIN, null);
        UserPrincipal principal = UserPrincipal.from(admin);
        Instant outsideWindow = CURRENT_TIME.minusSeconds(30 * 60 * 60);
        Stoppage toUpdate = stoppage(
                updatedId,
                station,
                StoppageReason.FALTA_DE_AGUA,
                outsideWindow,
                null,
                operator
        );
        Stoppage toDelete = stoppage(
                deletedId,
                station,
                StoppageReason.FALTA_DE_AGUA,
                outsideWindow,
                null,
                operator
        );
        StoppageRepository repository = mock(StoppageRepository.class);
        CurrentUserService currentUserService = mock(CurrentUserService.class);
        when(repository.findByIdAndDeletedFalse(updatedId)).thenReturn(Optional.of(toUpdate));
        when(repository.findByIdAndDeletedFalse(deletedId)).thenReturn(Optional.of(toDelete));
        when(repository.findOverlapConflicts(eq(station.getId()), any(), any(), any(), eq(updatedId)))
                .thenReturn(List.of());
        when(currentUserService.requireUser(principal)).thenReturn(admin);
        StoppageService service = new StoppageService(
                repository,
                mock(StationScopeService.class),
                currentUserService,
                mock(ShiftCalculator.class),
                mock(AuditService.class),
                mock(NotificationDispatcher.class),
                mock(NotificationRecipientResolver.class),
                fixedClock(),
                OperatorWindowPolicy.defaultWindow(fixedClock())
        );

        StoppageResponse updated = service.update(
                updatedId,
                new StoppageUpdateRequest(
                        StoppageReason.OUTROS,
                        "Motivo livre da parada",
                        outsideWindow,
                        CURRENT_TIME,
                        "Admin correcting an old stoppage"
                ),
                principal
        );

        assertThat(updated.reason()).isEqualTo(StoppageReason.OUTROS);
        assertThat(updated.endAt()).isEqualTo(CURRENT_TIME);

        service.delete(deletedId, new AuditDescriptionRequest("Admin removing an old stoppage"), principal);

        assertThat(toDelete.isDeleted()).isTrue();
        assertThat(toDelete.getDeletedBy()).isEqualTo(admin);
    }

    @Test
    void stoppageWriteEndpointsDoNotAllowSupervisor() {
        List<java.lang.reflect.Method> writeMethods = Arrays.stream(StoppageController.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(PostMapping.class)
                        || method.isAnnotationPresent(PutMapping.class)
                        || method.isAnnotationPresent(DeleteMapping.class))
                .toList();

        assertThat(writeMethods).isNotEmpty();
        assertThat(writeMethods).allSatisfy(method -> {
            PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);

            assertThat(preAuthorize).isNotNull();
            assertThat(preAuthorize.value()).contains("OPERATOR", "ADMIN");
            assertThat(preAuthorize.value()).doesNotContain("SUPERVISOR");
        });
    }

    private StoppageService service(UUID stoppageId, Stoppage stoppage, AppUser user, UserPrincipal principal) {
        return service(stoppageId, stoppage, user, principal, Clock.systemUTC());
    }

    private StoppageService service(
            UUID stoppageId,
            Stoppage stoppage,
            AppUser user,
            UserPrincipal principal,
            Clock clock
    ) {
        StoppageRepository repository = mock(StoppageRepository.class);
        StationScopeService stationScopeService = mock(StationScopeService.class);
        CurrentUserService currentUserService = mock(CurrentUserService.class);
        ShiftCalculator shiftCalculator = mock(ShiftCalculator.class);
        AuditService auditService = mock(AuditService.class);

        when(repository.findByIdAndDeletedFalse(stoppageId)).thenReturn(Optional.of(stoppage));
        when(currentUserService.requireUser(principal)).thenReturn(user);

        return new StoppageService(
                repository,
                stationScopeService,
                currentUserService,
                shiftCalculator,
                auditService,
                mock(NotificationDispatcher.class),
                mock(NotificationRecipientResolver.class),
                clock
        );
    }

    private StoppageService service(StoppageRepository repository, StationScopeService stationScopeService) {
        return service(repository, stationScopeService, Clock.systemUTC());
    }

    private StoppageService service(
            StoppageRepository repository,
            StationScopeService stationScopeService,
            Clock clock
    ) {
        return new StoppageService(
                repository,
                stationScopeService,
                mock(CurrentUserService.class),
                mock(ShiftCalculator.class),
                mock(AuditService.class),
                mock(NotificationDispatcher.class),
                mock(NotificationRecipientResolver.class),
                clock
        );
    }

    private Stoppage stoppage(
            UUID stoppageId,
            Station station,
            StoppageReason reason,
            Instant startAt,
            Instant endAt,
            AppUser user
    ) {
        Stoppage stoppage = new Stoppage(station, reason, startAt, user);
        ReflectionTestUtils.setField(stoppage, "id", stoppageId);
        if (endAt != null) {
            stoppage.finish(endAt);
        }
        return stoppage;
    }

    private AppUser user(UserRole role, Station station) {
        AppUser user = new AppUser("User", role.name().toLowerCase(), "hash", role, station, false);
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        return user;
    }

    private Station station(UUID stationId, String name) {
        Station station = new Station(name, name.toLowerCase().replace(" ", "-"));
        ReflectionTestUtils.setField(station, "id", stationId);
        return station;
    }

    private Clock fixedClock() {
        return Clock.fixed(CURRENT_TIME, ZoneOffset.UTC);
    }

    private void assertNoNotificationDispatch(StoppageService service) {
        NotificationDispatcher dispatcher = (NotificationDispatcher) ReflectionTestUtils.getField(
                service,
                "notificationDispatcher"
        );
        verifyNoInteractions(dispatcher);
    }
}
