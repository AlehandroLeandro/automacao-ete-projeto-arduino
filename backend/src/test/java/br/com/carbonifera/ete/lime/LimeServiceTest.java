package br.com.carbonifera.ete.lime;

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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.time.Instant;
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
class LimeServiceTest {

    private static final String INSUFFICIENT_STOCK_MESSAGE = "Estoque de cal insuficiente para este consumo";
    private static final String NOT_FOUND_MESSAGE = "Lime record not found";
    private static final Instant NOW = Instant.parse("2026-07-13T15:00:00Z");
    private static final Instant OUTSIDE_WINDOW_TIME = NOW.minusSeconds(30 * 60 * 60);

    @Mock
    private LimeRecordRepository repository;

    @Mock
    private StationScopeService stationScopeService;

    @Mock
    private CurrentUserService currentUserService;

    @Mock
    private LimeMinStockMonitor limeMinStockMonitor;

    private LimeService service;

    @BeforeEach
    void setUp() {
        service = new LimeService(
                repository,
                new LimeStockCalculator(),
                stationScopeService,
                currentUserService,
                limeMinStockMonitor
        );
    }

    @Test
    void listAcceptsOnlyEndAt() {
        UserPrincipal principal = UserPrincipal.from(user(UserRole.ADMIN, null));
        Station station = station(UUID.randomUUID(), "ITE A");
        Instant endAt = Instant.parse("2026-07-02T00:00:00Z");
        when(stationScopeService.resolveReadableStations(null, false, principal)).thenReturn(List.of(station));
        when(repository.findForList(List.of(station), null, null, endAt, null)).thenReturn(List.of());

        assertThat(service.list(
                null,
                false,
                null,
                null,
                endAt,
                principal
        )).isEmpty();

        verify(repository).findForList(List.of(station), null, null, endAt, null);
    }

    @Test
    void listRejectsEndAtBeforeStartAtBeforeResolvingScope() {
        UserPrincipal principal = UserPrincipal.from(user(UserRole.ADMIN, null));

        assertThatThrownBy(() -> service.list(
                null,
                false,
                null,
                Instant.parse("2026-07-02T00:00:00Z"),
                Instant.parse("2026-07-01T00:00:00Z"),
                principal
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Horário de fim deve ser maior que o horário de início");

        verifyNoInteractions(stationScopeService, repository);
    }

    @Test
    void listUsesReadableScopeAndCalculatesStockAfterBagsFromPreviousEvents() {
        Instant startAt = Instant.parse("2026-07-01T09:00:00Z");
        Instant endAt = Instant.parse("2026-07-01T11:00:00Z");
        Station station = station(UUID.randomUUID(), "ITE A");
        AppUser user = user(UserRole.OPERATOR, station);
        UserPrincipal principal = UserPrincipal.from(user);
        LimeRecord previousEntry = limeRecord(
                station,
                LimeRecordType.ENTRY,
                "100.00",
                Instant.parse("2026-07-01T08:00:00Z"),
                user
        );
        LimeRecord entry = limeRecord(
                station,
                LimeRecordType.ENTRY,
                "20.00",
                startAt,
                user
        );
        LimeRecord consumption = limeRecord(
                station,
                LimeRecordType.CONSUMPTION,
                "15.00",
                Instant.parse("2026-07-01T10:00:00Z"),
                user
        );
        List<Station> readableStations = List.of(station);

        when(stationScopeService.resolveReadableStations(null, true, principal)).thenReturn(readableStations);
        when(repository.findForList(readableStations, null, startAt, endAt, user.getId()))
                .thenReturn(List.of(consumption, entry));
        when(repository.findAllByStationAndRecordedAtLessThanEqualOrderByRecordedAtAsc(
                station,
                consumption.getRecordedAt()
        ))
                .thenReturn(List.of(previousEntry, entry, consumption));
        when(repository.findAllByStationAndRecordedAtLessThanEqualOrderByRecordedAtAsc(
                station,
                entry.getRecordedAt()
        ))
                .thenReturn(List.of(previousEntry, entry));

        List<LimeRecordListResponse> response = service.list(null, true, null, startAt, endAt, principal);

        assertThat(response)
                .extracting(LimeRecordListResponse::id)
                .containsExactly(consumption.getId(), entry.getId());
        assertThat(response)
                .extracting(LimeRecordListResponse::stockAfterBags)
                .containsExactly(new BigDecimal("105.00"), new BigDecimal("120.00"));
        verify(stationScopeService).resolveReadableStations(null, true, principal);
        verify(repository).findForList(readableStations, null, startAt, endAt, user.getId());
        verify(repository).findAllByStationAndRecordedAtLessThanEqualOrderByRecordedAtAsc(
                station,
                consumption.getRecordedAt()
        );
        verify(repository).findAllByStationAndRecordedAtLessThanEqualOrderByRecordedAtAsc(
                station,
                entry.getRecordedAt()
        );
    }

    @Test
    void listPassesStationTypeAndPeriodFiltersToRepository() {
        UUID stationId = UUID.randomUUID();
        Instant startAt = Instant.parse("2026-07-01T00:00:00Z");
        Instant endAt = Instant.parse("2026-07-02T00:00:00Z");
        Station station = station(stationId, "ITE A");
        UserPrincipal principal = UserPrincipal.from(user(UserRole.ADMIN, null));
        List<Station> readableStations = List.of(station);

        when(stationScopeService.resolveReadableStations(stationId, false, principal)).thenReturn(readableStations);
        when(repository.findForList(readableStations, LimeRecordType.CONSUMPTION, startAt, endAt, null))
                .thenReturn(List.of());

        List<LimeRecordListResponse> response = service.list(
                stationId,
                false,
                LimeRecordType.CONSUMPTION,
                startAt,
                endAt,
                principal
        );

        assertThat(response).isEmpty();
        verify(stationScopeService).resolveReadableStations(stationId, false, principal);
        verify(repository).findForList(readableStations, LimeRecordType.CONSUMPTION, startAt, endAt, null);
    }

    @Test
    void pagedListRejectsUnsupportedPageSize() {
        assertThatThrownBy(() -> service.list(null, false, null, NOW.minusSeconds(10), NOW, null, 0, 25,
                UserPrincipal.from(user(UserRole.ADMIN, null))))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Page size must be one of: 10, 50, 100");
    }

    @Test
    void pagedListUsesOperatorAuthorOverRequestedAuthor() {
        Station station = station(UUID.randomUUID(), "ITE A");
        AppUser operator = user(UserRole.OPERATOR, station);
        UserPrincipal principal = UserPrincipal.from(operator);
        when(stationScopeService.resolveReadableStations(null, false, principal)).thenReturn(List.of(station));
        when(repository.findForList(any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service.list(null, false, null, NOW.minusSeconds(10), NOW, UUID.randomUUID(), 0, 10, principal);

        verify(repository).findForList(List.of(station), null, NOW.minusSeconds(10), NOW, operator.getId(),
                PageRequest.of(0, 10, Sort.by(Sort.Order.desc("recordedAt"), Sort.Order.desc("id"))));
    }

    @Test
    void pagedListUsesRequestedAuthorForAdmin() {
        Station station = station(UUID.randomUUID(), "ITE A");
        UserPrincipal principal = UserPrincipal.from(user(UserRole.ADMIN, null));
        UUID requestedAuthor = UUID.randomUUID();
        when(stationScopeService.resolveReadableStations(null, false, principal)).thenReturn(List.of(station));
        when(repository.findForList(any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        assertThat(service.list(null, false, null, NOW.minusSeconds(10), NOW, requestedAuthor, 1, 50, principal))
                .isEmpty();

        verify(repository).findForList(List.of(station), null, NOW.minusSeconds(10), NOW, requestedAuthor,
                PageRequest.of(1, 50, Sort.by(Sort.Order.desc("recordedAt"), Sort.Order.desc("id"))));
    }

    @Test
    void createRejectsConsumptionWhenProjectedStockWouldBeNegative() {
        Station station = station(UUID.randomUUID(), "ITE A");
        AppUser user = user(UserRole.ADMIN, null);
        UserPrincipal principal = UserPrincipal.from(user);
        LimeRecord entry = limeRecord(
                station,
                LimeRecordType.ENTRY,
                "10.00",
                Instant.parse("2026-07-01T08:00:00Z"),
                user
        );
        LimeRecordRequest request = request(
                station,
                LimeRecordType.CONSUMPTION,
                "11.00",
                "2026-07-01T09:00:00Z"
        );

        when(stationScopeService.requireWritableStation(station.getId(), principal)).thenReturn(station);
        when(repository.findAllByStationOrderByRecordedAtAsc(station)).thenReturn(List.of(entry));

        assertThatThrownBy(() -> service.create(request, principal))
                .isInstanceOf(BusinessException.class)
                .hasMessage(INSUFFICIENT_STOCK_MESSAGE);

        verify(repository, never()).save(any());
        verifyNoInteractions(currentUserService);
        verifyNoInteractions(limeMinStockMonitor);
    }

    @Test
    void createAllowsConsumptionThatLeavesStockAtZero() {
        Station station = station(UUID.randomUUID(), "ITE A");
        AppUser user = user(UserRole.ADMIN, null);
        UserPrincipal principal = UserPrincipal.from(user);
        LimeRecord entry = limeRecord(
                station,
                LimeRecordType.ENTRY,
                "10.00",
                Instant.parse("2026-07-01T08:00:00Z"),
                user
        );
        LimeRecordRequest request = request(
                station,
                LimeRecordType.CONSUMPTION,
                "10.00",
                "2026-07-01T09:00:00Z"
        );

        when(stationScopeService.requireWritableStation(station.getId(), principal)).thenReturn(station);
        when(repository.findAllByStationOrderByRecordedAtAsc(station)).thenReturn(List.of(entry));
        when(currentUserService.requireUser(principal)).thenReturn(user);
        when(repository.save(any(LimeRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LimeRecordResponse response = service.create(request, principal);

        assertThat(response.quantityBags()).isEqualByComparingTo("10.00");
        verify(repository).save(any(LimeRecord.class));
        verify(limeMinStockMonitor).evaluate(station);
    }

    @Test
    void createRejectsQuantityThatIsNotGreaterThanZero() {
        UserPrincipal principal = UserPrincipal.from(user(UserRole.ADMIN, null));
        LimeRecordRequest request = new LimeRecordRequest(
                UUID.randomUUID(),
                LimeRecordType.ENTRY,
                BigDecimal.ZERO,
                Instant.parse("2026-07-01T09:00:00Z"),
                null
        );

        assertThatThrownBy(() -> service.create(request, principal))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Quantidade de sacos deve ser maior que zero");

        verifyNoInteractions(stationScopeService, repository, currentUserService, limeMinStockMonitor);
    }

    @Test
    void updateRejectsConsumptionWhenProjectedStockWouldBeNegative() {
        Station station = station(UUID.randomUUID(), "ITE A");
        AppUser user = user(UserRole.ADMIN, null);
        UserPrincipal principal = UserPrincipal.from(user);
        LimeRecord entry = limeRecord(
                station,
                LimeRecordType.ENTRY,
                "10.00",
                Instant.parse("2026-07-01T08:00:00Z"),
                user
        );
        LimeRecord consumption = limeRecord(
                station,
                LimeRecordType.CONSUMPTION,
                "5.00",
                Instant.parse("2026-07-01T09:00:00Z"),
                user
        );
        LimeRecordRequest request = request(
                station,
                LimeRecordType.CONSUMPTION,
                "11.00",
                "2026-07-01T09:00:00Z"
        );

        when(repository.findById(consumption.getId())).thenReturn(Optional.of(consumption));
        doNothing().when(stationScopeService).requireWritableStation(station, principal);
        when(stationScopeService.requireWritableStation(station.getId(), principal)).thenReturn(station);
        when(repository.findAllByStationOrderByRecordedAtAsc(station)).thenReturn(List.of(entry, consumption));

        assertThatThrownBy(() -> service.update(consumption.getId(), request, principal))
                .isInstanceOf(BusinessException.class)
                .hasMessage(INSUFFICIENT_STOCK_MESSAGE);

        assertThat(consumption.getQuantityBags()).isEqualByComparingTo("5.00");
        verifyNoInteractions(limeMinStockMonitor);
    }

    @Test
    void updateRejectsEntryReductionWhenLaterConsumptionWouldBecomeNegative() {
        Station station = station(UUID.randomUUID(), "ITE A");
        AppUser user = user(UserRole.ADMIN, null);
        UserPrincipal principal = UserPrincipal.from(user);
        LimeRecord entry = limeRecord(
                station,
                LimeRecordType.ENTRY,
                "10.00",
                Instant.parse("2026-07-01T09:00:00Z"),
                user
        );
        LimeRecord laterConsumption = limeRecord(
                station,
                LimeRecordType.CONSUMPTION,
                "8.00",
                Instant.parse("2026-07-01T10:00:00Z"),
                user
        );
        LimeRecordRequest request = request(
                station,
                LimeRecordType.ENTRY,
                "5.00",
                "2026-07-01T09:00:00Z"
        );

        when(repository.findById(entry.getId())).thenReturn(Optional.of(entry));
        doNothing().when(stationScopeService).requireWritableStation(station, principal);
        when(stationScopeService.requireWritableStation(station.getId(), principal)).thenReturn(station);
        when(repository.findAllByStationOrderByRecordedAtAsc(station)).thenReturn(List.of(entry, laterConsumption));

        assertThatThrownBy(() -> service.update(entry.getId(), request, principal))
                .isInstanceOf(BusinessException.class)
                .hasMessage(INSUFFICIENT_STOCK_MESSAGE);

        assertThat(entry.getQuantityBags()).isEqualByComparingTo("10.00");
        verifyNoInteractions(limeMinStockMonitor);
    }

    @Test
    void updateEvaluatesPreviousAndNewStationWhenRecordMovesStation() {
        Station previousStation = station(UUID.randomUUID(), "ITE A");
        Station newStation = station(UUID.randomUUID(), "ITE B");
        AppUser user = user(UserRole.ADMIN, null);
        UserPrincipal principal = UserPrincipal.from(user);
        LimeRecord entry = limeRecord(
                previousStation,
                LimeRecordType.ENTRY,
                "10.00",
                Instant.parse("2026-07-01T09:00:00Z"),
                user
        );
        LimeRecordRequest request = request(
                newStation,
                LimeRecordType.ENTRY,
                "12.00",
                "2026-07-01T09:30:00Z"
        );

        when(repository.findById(entry.getId())).thenReturn(Optional.of(entry));
        doNothing().when(stationScopeService).requireWritableStation(previousStation, principal);
        when(stationScopeService.requireWritableStation(newStation.getId(), principal)).thenReturn(newStation);
        when(repository.findAllByStationOrderByRecordedAtAsc(previousStation)).thenReturn(List.of(entry));
        when(repository.findAllByStationOrderByRecordedAtAsc(newStation)).thenReturn(List.of());

        LimeRecordResponse response = service.update(entry.getId(), request, principal);

        assertThat(response.stationId()).isEqualTo(newStation.getId());
        verify(limeMinStockMonitor).evaluate(previousStation);
        verify(limeMinStockMonitor).evaluate(newStation);
    }

    @Test
    void deleteRejectsEntryRemovalWhenLaterConsumptionWouldBecomeNegative() {
        Station station = station(UUID.randomUUID(), "ITE A");
        AppUser user = user(UserRole.ADMIN, null);
        UserPrincipal principal = UserPrincipal.from(user);
        LimeRecord entry = limeRecord(
                station,
                LimeRecordType.ENTRY,
                "10.00",
                Instant.parse("2026-07-01T09:00:00Z"),
                user
        );
        LimeRecord laterConsumption = limeRecord(
                station,
                LimeRecordType.CONSUMPTION,
                "8.00",
                Instant.parse("2026-07-01T10:00:00Z"),
                user
        );

        when(repository.findById(entry.getId())).thenReturn(Optional.of(entry));
        doNothing().when(stationScopeService).requireWritableStation(station, principal);
        when(repository.findAllByStationOrderByRecordedAtAsc(station)).thenReturn(List.of(entry, laterConsumption));

        assertThatThrownBy(() -> service.delete(entry.getId(), principal))
                .isInstanceOf(BusinessException.class)
                .hasMessage(INSUFFICIENT_STOCK_MESSAGE);

        verify(repository, never()).delete(any());
        verifyNoInteractions(limeMinStockMonitor);
    }

    @Test
    void operatorListFiltersByOwnAuthorshipAndKeepsRequestedStartAt() {
        UUID stationId = UUID.randomUUID();
        Instant startAt = Instant.parse("2026-06-01T00:00:00Z");
        Instant endAt = Instant.parse("2026-07-14T00:00:00Z");
        Station station = station(stationId, "ITE A");
        AppUser operator = user(UserRole.OPERATOR, station);
        UserPrincipal principal = UserPrincipal.from(operator);
        LimeRecord ownRecord = limeRecord(station, LimeRecordType.ENTRY, "20.00", OUTSIDE_WINDOW_TIME, operator);
        List<Station> readableStations = List.of(station);

        when(stationScopeService.resolveReadableStations(stationId, false, principal)).thenReturn(readableStations);
        when(repository.findForList(readableStations, null, startAt, endAt, operator.getId()))
                .thenReturn(List.of(ownRecord));
        when(repository.findAllByStationAndRecordedAtLessThanEqualOrderByRecordedAtAsc(station, OUTSIDE_WINDOW_TIME))
                .thenReturn(List.of(ownRecord));

        List<LimeRecordListResponse> response = service.list(stationId, false, null, startAt, endAt, principal);

        assertThat(response)
                .extracting(LimeRecordListResponse::id)
                .containsExactly(ownRecord.getId());
        verify(repository).findForList(readableStations, null, startAt, endAt, operator.getId());
    }

    @Test
    void supervisorListIsNotFilteredByAuthorship() {
        UUID stationId = UUID.randomUUID();
        Instant startAt = Instant.parse("2026-07-01T00:00:00Z");
        Instant endAt = Instant.parse("2026-07-02T00:00:00Z");
        Station station = station(stationId, "ITE A");
        UserPrincipal principal = UserPrincipal.from(user(UserRole.SUPERVISOR, null));
        List<Station> readableStations = List.of(station);

        when(stationScopeService.resolveReadableStations(stationId, false, principal)).thenReturn(readableStations);
        when(repository.findForList(readableStations, null, startAt, endAt, null)).thenReturn(List.of());

        service.list(stationId, false, null, startAt, endAt, principal);

        verify(repository).findForList(readableStations, null, startAt, endAt, null);
    }

    @Test
    void operatorUpdateOfForeignRecordIsNotFoundAndDoesNotChangeAnything() {
        Station station = station(UUID.randomUUID(), "ITE A");
        AppUser operator = user(UserRole.OPERATOR, station);
        AppUser otherOperator = user(UserRole.OPERATOR, station);
        UserPrincipal principal = UserPrincipal.from(operator);
        LimeRecord foreign = limeRecord(station, LimeRecordType.CONSUMPTION, "2.00", NOW, otherOperator);
        LimeRecordRequest request = request(station, LimeRecordType.CONSUMPTION, "9.00", "2026-07-13T14:00:00Z");

        when(repository.findById(foreign.getId())).thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> service.update(foreign.getId(), request, principal))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage(NOT_FOUND_MESSAGE);

        assertThat(foreign.getQuantityBags()).isEqualByComparingTo(new BigDecimal("2.00"));
        verifyNoInteractions(stationScopeService, limeMinStockMonitor);
    }

    @Test
    void operatorDeleteOfForeignRecordIsNotFoundAndDoesNotRemoveIt() {
        Station station = station(UUID.randomUUID(), "ITE A");
        AppUser operator = user(UserRole.OPERATOR, station);
        AppUser otherOperator = user(UserRole.OPERATOR, station);
        UserPrincipal principal = UserPrincipal.from(operator);
        LimeRecord foreign = limeRecord(station, LimeRecordType.CONSUMPTION, "2.00", NOW, otherOperator);

        when(repository.findById(foreign.getId())).thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> service.delete(foreign.getId(), principal))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage(NOT_FOUND_MESSAGE);

        verify(repository, never()).delete(any());
        verifyNoInteractions(stationScopeService, limeMinStockMonitor);
    }

    @Test
    void operatorUpdatesOwnRecordOutsideRecordWindowToADateAlsoOutsideTheWindow() {
        Station station = station(UUID.randomUUID(), "ITE A");
        AppUser operator = user(UserRole.OPERATOR, station);
        UserPrincipal principal = UserPrincipal.from(operator);
        LimeRecord ownRecord = limeRecord(station, LimeRecordType.ENTRY, "20.00", OUTSIDE_WINDOW_TIME, operator);
        Instant newRecordedAt = OUTSIDE_WINDOW_TIME.minusSeconds(3600);
        LimeRecordRequest request = new LimeRecordRequest(
                station.getId(),
                LimeRecordType.ENTRY,
                new BigDecimal("25.00"),
                newRecordedAt,
                null
        );

        when(repository.findById(ownRecord.getId())).thenReturn(Optional.of(ownRecord));
        doNothing().when(stationScopeService).requireWritableStation(station, principal);
        when(stationScopeService.requireWritableStation(station.getId(), principal)).thenReturn(station);
        when(repository.findAllByStationOrderByRecordedAtAsc(station)).thenReturn(List.of(ownRecord));

        LimeRecordResponse response = service.update(ownRecord.getId(), request, principal);

        assertThat(response.quantityBags()).isEqualByComparingTo(new BigDecimal("25.00"));
        assertThat(response.recordedAt()).isEqualTo(newRecordedAt);
    }

    @Test
    void operatorDeletesOwnRecordOutsideRecordWindow() {
        Station station = station(UUID.randomUUID(), "ITE A");
        AppUser operator = user(UserRole.OPERATOR, station);
        UserPrincipal principal = UserPrincipal.from(operator);
        LimeRecord ownRecord = limeRecord(station, LimeRecordType.ENTRY, "20.00", OUTSIDE_WINDOW_TIME, operator);

        when(repository.findById(ownRecord.getId())).thenReturn(Optional.of(ownRecord));
        doNothing().when(stationScopeService).requireWritableStation(station, principal);
        when(repository.findAllByStationOrderByRecordedAtAsc(station)).thenReturn(List.of(ownRecord));

        service.delete(ownRecord.getId(), principal);

        verify(repository).delete(ownRecord);
        verify(limeMinStockMonitor).evaluate(station);
    }

    @Test
    void operatorCurrentStockAggregatesRecordsFromEveryAuthorOfTheStation() {
        UUID stationId = UUID.randomUUID();
        Station station = station(stationId, "ITE A");
        AppUser operator = user(UserRole.OPERATOR, station);
        AppUser otherOperator = user(UserRole.OPERATOR, station);
        UserPrincipal principal = UserPrincipal.from(operator);
        Instant asOf = Instant.parse("2026-07-13T12:00:00Z");
        LimeRecord entryFromOperator = limeRecord(station, LimeRecordType.ENTRY, "10.00", asOf.minusSeconds(7200), operator);
        LimeRecord entryFromOther = limeRecord(station, LimeRecordType.ENTRY, "30.00", asOf.minusSeconds(3600), otherOperator);

        when(stationScopeService.requireReadableStation(stationId, principal)).thenReturn(station);
        when(repository.findAllByStationAndRecordedAtLessThanEqualOrderByRecordedAtAsc(station, asOf))
                .thenReturn(List.of(entryFromOperator, entryFromOther));

        LimeStockResponse response = service.currentStock(stationId, asOf, principal);

        assertThat(response.currentStockBags()).isEqualByComparingTo(new BigDecimal("40.00"));
    }

    @Test
    void operatorConsumptionIsValidatedAgainstTheWholeStationBalanceNotOnlyItsOwnRecords() {
        Station station = station(UUID.randomUUID(), "ITE A");
        AppUser operator = user(UserRole.OPERATOR, station);
        AppUser otherOperator = user(UserRole.OPERATOR, station);
        UserPrincipal principal = UserPrincipal.from(operator);
        LimeRecord ownEntry = limeRecord(station, LimeRecordType.ENTRY, "10.00", Instant.parse("2026-07-13T08:00:00Z"), operator);
        LimeRecord otherConsumption = limeRecord(station, LimeRecordType.CONSUMPTION, "8.00", Instant.parse("2026-07-13T09:00:00Z"), otherOperator);
        LimeRecordRequest request = request(station, LimeRecordType.CONSUMPTION, "5.00", "2026-07-13T10:00:00Z");

        when(stationScopeService.requireWritableStation(station.getId(), principal)).thenReturn(station);
        when(repository.findAllByStationOrderByRecordedAtAsc(station)).thenReturn(List.of(ownEntry, otherConsumption));

        assertThatThrownBy(() -> service.create(request, principal))
                .isInstanceOf(BusinessException.class)
                .hasMessage(INSUFFICIENT_STOCK_MESSAGE);

        verify(repository, never()).save(any(LimeRecord.class));
        verifyNoInteractions(limeMinStockMonitor);
    }

    @Test
    void deleteEvaluatesStationAfterRemovingRecord() {
        Station station = station(UUID.randomUUID(), "ITE A");
        AppUser user = user(UserRole.ADMIN, null);
        UserPrincipal principal = UserPrincipal.from(user);
        LimeRecord consumption = limeRecord(
                station,
                LimeRecordType.CONSUMPTION,
                "2.00",
                Instant.parse("2026-07-01T10:00:00Z"),
                user
        );

        when(repository.findById(consumption.getId())).thenReturn(Optional.of(consumption));
        doNothing().when(stationScopeService).requireWritableStation(station, principal);
        when(repository.findAllByStationOrderByRecordedAtAsc(station)).thenReturn(List.of(consumption));

        service.delete(consumption.getId(), principal);

        verify(repository).delete(consumption);
        verify(limeMinStockMonitor).evaluate(station);
    }

    @Test
    void createValidatesOnlyTheSelectedStationStock() {
        Station selectedStation = station(UUID.randomUUID(), "ITE A");
        Station otherStation = station(UUID.randomUUID(), "ITE B");
        AppUser user = user(UserRole.ADMIN, null);
        UserPrincipal principal = UserPrincipal.from(user);
        LimeRecord selectedEntry = limeRecord(
                selectedStation,
                LimeRecordType.ENTRY,
                "10.00",
                Instant.parse("2026-07-01T08:00:00Z"),
                user
        );
        LimeRecordRequest request = request(
                selectedStation,
                LimeRecordType.CONSUMPTION,
                "5.00",
                "2026-07-01T09:00:00Z"
        );

        when(stationScopeService.requireWritableStation(selectedStation.getId(), principal)).thenReturn(selectedStation);
        when(repository.findAllByStationOrderByRecordedAtAsc(selectedStation)).thenReturn(List.of(selectedEntry));
        when(currentUserService.requireUser(principal)).thenReturn(user);
        when(repository.save(any(LimeRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LimeRecordResponse response = service.create(request, principal);

        assertThat(response.stationId()).isEqualTo(selectedStation.getId());
        verify(repository).findAllByStationOrderByRecordedAtAsc(selectedStation);
        verify(repository, never()).findAllByStationOrderByRecordedAtAsc(otherStation);
        verify(limeMinStockMonitor).evaluate(selectedStation);
    }

    private LimeRecord limeRecord(
            Station station,
            LimeRecordType type,
            String quantityBags,
            Instant recordedAt,
            AppUser user
    ) {
        LimeRecord record = new LimeRecord(
                station,
                type,
                new BigDecimal(quantityBags),
                recordedAt,
                null,
                user
        );
        ReflectionTestUtils.setField(record, "id", UUID.randomUUID());
        return record;
    }

    private LimeRecordRequest request(
            Station station,
            LimeRecordType type,
            String quantityBags,
            String recordedAt
    ) {
        return new LimeRecordRequest(
                station.getId(),
                type,
                new BigDecimal(quantityBags),
                Instant.parse(recordedAt),
                null
        );
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
}
