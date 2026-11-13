package br.com.carbonifera.ete.dashboard;

import br.com.carbonifera.ete.lime.LimeRecord;
import br.com.carbonifera.ete.lime.LimeRecordRepository;
import br.com.carbonifera.ete.lime.LimeRecordType;
import br.com.carbonifera.ete.lime.LimeService;
import br.com.carbonifera.ete.lime.LimeStockResponse;
import br.com.carbonifera.ete.measurement.FlowMeasurement;
import br.com.carbonifera.ete.measurement.FlowMeasurementRepository;
import br.com.carbonifera.ete.measurement.PhMeasurement;
import br.com.carbonifera.ete.measurement.PhMeasurementRepository;
import br.com.carbonifera.ete.measurement.PhWaterType;
import br.com.carbonifera.ete.security.CurrentUserService;
import br.com.carbonifera.ete.security.UserPrincipal;
import br.com.carbonifera.ete.station.Station;
import br.com.carbonifera.ete.station.StationScopeService;
import br.com.carbonifera.ete.station.StationService;
import br.com.carbonifera.ete.stoppage.ShiftCalculator;
import br.com.carbonifera.ete.stoppage.ShiftType;
import br.com.carbonifera.ete.stoppage.ShiftWindow;
import br.com.carbonifera.ete.stoppage.Stoppage;
import br.com.carbonifera.ete.stoppage.StoppageRepository;
import br.com.carbonifera.ete.stoppage.StoppageReason;
import br.com.carbonifera.ete.stoppage.StoppageStatus;
import br.com.carbonifera.ete.user.AppUser;
import br.com.carbonifera.ete.user.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DashboardServiceTest {

    @Test
    void springCanInstantiateDashboardServiceBean() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(StationService.class, () -> mock(StationService.class));
            context.registerBean(LimeService.class, () -> mock(LimeService.class));
            context.registerBean(LimeRecordRepository.class, () -> mock(LimeRecordRepository.class));
            context.registerBean(PhMeasurementRepository.class, () -> mock(PhMeasurementRepository.class));
            context.registerBean(FlowMeasurementRepository.class, () -> mock(FlowMeasurementRepository.class));
            context.registerBean(StoppageRepository.class, () -> mock(StoppageRepository.class));
            context.registerBean(ShiftCalculator.class, () -> mock(ShiftCalculator.class));
            context.registerBean(StationScopeService.class, () -> mock(StationScopeService.class));
            context.register(DashboardService.class);

            context.refresh();

            assertThat(context.getBean(DashboardService.class)).isNotNull();
        }
    }

    @Test
    void dashboardRequiresAuthenticatedPrincipal() {
        Station firstStation = station(UUID.randomUUID(), "ITE A");
        Station secondStation = station(UUID.randomUUID(), "ITE B");
        DashboardService service = context(List.of(firstStation, secondStation), null).service();

        assertThatThrownBy(() -> service.dashboard(null, true, null))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class)
                .hasMessage("Authenticated principal is required");
    }

    @Test
    void authenticatedOperatorDashboardUsesAssignedStationOnly() {
        Station assignedStation = station(UUID.randomUUID(), "ITE A");
        Station secondStation = station(UUID.randomUUID(), "ITE B");
        AppUser operator = user(UserRole.OPERATOR, assignedStation);
        DashboardService service = context(List.of(assignedStation, secondStation), operator).service();

        DashboardResponse response = service.dashboard(null, true, UserPrincipal.from(operator));

        assertThat(response.consolidated()).isFalse();
        assertThat(response.stationId()).isEqualTo(assignedStation.getId());
        assertThat(response.stations())
                .extracting(StationDashboardResponse::stationId)
                .containsExactly(assignedStation.getId());
    }

    @Test
    void authenticatedOperatorDashboardIgnoresDifferentRequestedStation() {
        Station assignedStation = station(UUID.randomUUID(), "ITE A");
        Station requestedStation = station(UUID.randomUUID(), "ITE B");
        AppUser operator = user(UserRole.OPERATOR, assignedStation);
        DashboardService service = context(List.of(assignedStation, requestedStation), operator).service();

        DashboardResponse response = service.dashboard(requestedStation.getId(), true, UserPrincipal.from(operator));

        assertThat(response.consolidated()).isFalse();
        assertThat(response.stationId()).isEqualTo(assignedStation.getId());
        assertThat(response.stations())
                .extracting(StationDashboardResponse::stationId)
                .containsExactly(assignedStation.getId());
    }

    @Test
    void operatorDashboardKeepsAssignedStationScopeAndPrunesSeries() {
        Station assignedStation = station(UUID.randomUUID(), "ITE A");
        Station requestedStation = station(UUID.randomUUID(), "ITE B");
        AppUser operator = user(UserRole.OPERATOR, assignedStation);
        DashboardServiceTestContext context = context(List.of(assignedStation, requestedStation), operator);
        Instant startAt = Instant.parse("2026-06-24T00:00:00Z");
        Instant endAt = Instant.parse("2026-07-01T00:00:00Z");

        when(context.phMeasurementRepository().findDashboardRows(
                eq(PhWaterType.RAW),
                anyCollection(),
                any(Instant.class),
                any(Instant.class)
        )).thenReturn(List.of(
                phMeasurement(assignedStation, PhWaterType.RAW, "6.50", startAt.plusSeconds(60), operator),
                phMeasurement(requestedStation, PhWaterType.RAW, "8.50", startAt.plusSeconds(120), operator)
        ));

        DashboardResponse response = context.service()
                .dashboard(requestedStation.getId(), true, startAt, endAt, UserPrincipal.from(operator));

        assertThat(response.stationId()).isEqualTo(assignedStation.getId());
        assertThat(response.stations())
                .extracting(StationDashboardResponse::stationId)
                .containsExactly(assignedStation.getId());
        assertThat(response.series()).isNull();
    }

    @Test
    void operatorDashboardPrunesSeriesActivityLatestReadingsAndLimeStock() {
        Station station = station(UUID.randomUUID(), "ITE A");
        AppUser operator = user(UserRole.OPERATOR, station);
        Instant now = Instant.parse("2026-07-07T13:00:00Z");
        Instant startOfDay = Instant.parse("2026-07-07T03:00:00Z");
        DashboardServiceTestContext context = context(List.of(station), operator, now);
        stubPrunableData(context, station, operator, now);
        when(context.limeService().sumConsumption(any(Station.class), any(Instant.class), any(Instant.class)))
                .thenReturn(new BigDecimal("4.00"));
        when(context.phMeasurementRepository().findDashboardRows(
                eq(PhWaterType.RAW),
                anyCollection(),
                eq(startOfDay),
                eq(now)
        )).thenReturn(List.of(phMeasurement(station, PhWaterType.RAW, "6.00", startOfDay, operator)));

        DashboardResponse response = context.service()
                .dashboard(station.getId(), false, UserPrincipal.from(operator));

        assertThat(response.limeStockBags()).isNull();
        assertThat(response.series()).isNull();
        assertThat(response.activity()).isNull();
        assertThat(response.latestRawPh()).isNull();
        assertThat(response.latestTreatedPh()).isNull();
        assertThat(response.latestFlow()).isNull();

        assertThat(response.stations()).hasSize(1);
        StationDashboardResponse stationDashboard = response.stations().get(0);
        assertThat(stationDashboard.limeStockBags()).isNull();
        assertThat(stationDashboard.latestRawPh()).isNull();
        assertThat(stationDashboard.latestTreatedPh()).isNull();
        assertThat(stationDashboard.latestFlow()).isNull();

        assertThat(response.consolidated()).isFalse();
        assertThat(response.stationId()).isEqualTo(station.getId());
        assertThat(response.generatedAt()).isEqualTo(now);
        assertThat(response.period()).isEqualTo(new DashboardPeriodResponse(now.minus(Duration.ofDays(7)), now));
        assertThat(response.limeConsumptionLastShiftBags()).isEqualByComparingTo("4.00");
        assertThat(response.currentDayAverages().rawPh().value()).isEqualByComparingTo("6.00");
        assertThat(response.currentDayAverages().workedHoursToday()).isEqualByComparingTo("10.00");
        assertThat(response.operation().status()).isEqualTo(OperationStatus.WORKING);
        assertThat(stationDashboard.limeConsumptionLastShiftBags()).isEqualByComparingTo("4.00");
        assertThat(stationDashboard.workedHoursToday()).isEqualByComparingTo("10.00");
        assertThat(stationDashboard.operation().status()).isEqualTo(OperationStatus.WORKING);

        verify(context.limeService(), never()).currentStock(any(Station.class), any(Instant.class));
        verify(context.phMeasurementRepository(), never())
                .findTopByStationInAndTypeAndDeletedFalseOrderByMeasuredAtDesc(anyCollection(), any());
        verify(context.flowMeasurementRepository(), never())
                .findTopByStationInAndDeletedFalseOrderByMeasuredAtDesc(anyCollection());
        verify(context.stoppageRepository(), never())
                .findRecentForActivity(anyCollection(), any(Instant.class), any(Instant.class));
    }

    @Test
    void operatorDashboardWithoutAnyReadingKeepsPrunedFieldsNullWithoutFailing() {
        Station station = station(UUID.randomUUID(), "ITE A");
        AppUser operator = user(UserRole.OPERATOR, station);
        DashboardServiceTestContext context = context(List.of(station), operator);

        DashboardResponse response = context.service()
                .dashboard(station.getId(), false, UserPrincipal.from(operator));

        assertThat(response.limeStockBags()).isNull();
        assertThat(response.stations().get(0).limeStockBags()).isNull();
        assertThat(response.currentDayAverages().rawPh()).isNull();
        assertThat(response.limeConsumptionLastShiftBags()).isEqualByComparingTo("0.00");
    }

    @Test
    void adminDashboardKeepsEveryPrunableFieldPopulated() {
        Station station = station(UUID.randomUUID(), "ITE A");
        AppUser admin = user(UserRole.ADMIN, null);
        Instant now = Instant.parse("2026-07-07T13:00:00Z");
        DashboardServiceTestContext context = context(List.of(station), admin, now);
        stubPrunableData(context, station, admin, now);

        DashboardResponse response = context.service()
                .dashboard(station.getId(), false, UserPrincipal.from(admin));

        assertFullDashboard(response);
    }

    @Test
    void supervisorDashboardKeepsEveryPrunableFieldPopulated() {
        Station station = station(UUID.randomUUID(), "ITE A");
        AppUser supervisor = user(UserRole.SUPERVISOR, station);
        Instant now = Instant.parse("2026-07-07T13:00:00Z");
        DashboardServiceTestContext context = context(List.of(station), supervisor, now);
        stubPrunableData(context, station, supervisor, now);

        DashboardResponse response = context.service()
                .dashboard(station.getId(), false, UserPrincipal.from(supervisor));

        assertFullDashboard(response);
    }

    @Test
    void viewerDashboardUsesAssignedStationOnly() {
        Station assignedStation = station(UUID.randomUUID(), "ITE A");
        Station requestedStation = station(UUID.randomUUID(), "ITE B");
        AppUser viewer = user(UserRole.VIEWER, assignedStation);
        DashboardService service = context(List.of(assignedStation, requestedStation), viewer).service();

        DashboardResponse response = service.dashboard(requestedStation.getId(), true, UserPrincipal.from(viewer));

        assertThat(response.consolidated()).isFalse();
        assertThat(response.stationId()).isEqualTo(assignedStation.getId());
        assertThat(response.stations())
                .extracting(StationDashboardResponse::stationId)
                .containsExactly(assignedStation.getId());
    }

    @Test
    void viewerDashboardKeepsFlowAndStoppagesAndPrunesPhAndLime() {
        Station station = station(UUID.randomUUID(), "ITE A");
        AppUser viewer = user(UserRole.VIEWER, station);
        Instant now = Instant.parse("2026-07-07T13:00:00Z");
        DashboardServiceTestContext context = context(List.of(station), viewer, now);
        stubPrunableData(context, station, viewer, now);
        when(context.flowMeasurementRepository().findDashboardRows(
                anyCollection(),
                any(Instant.class),
                any(Instant.class)
        )).thenReturn(List.of(flowMeasurement(station, "150.00", now.minusSeconds(60), viewer)));

        DashboardResponse response = context.service()
                .dashboard(station.getId(), false, UserPrincipal.from(viewer));

        assertThat(response.latestFlow().value()).isEqualByComparingTo("150.00");
        assertThat(response.series().flow().get(0).points()).hasSize(1);
        assertThat(response.series().rawPh()).isEmpty();
        assertThat(response.series().treatedPh()).isEmpty();
        assertThat(response.series().lime()).isEmpty();
        assertThat(response.activity().stoppageLogs()).hasSize(1);
        assertThat(response.activity().limeLogs()).isEmpty();
        assertThat(response.currentDayAverages().flow().value()).isEqualByComparingTo("150.00");
        assertThat(response.currentDayAverages().rawPh()).isNull();
        assertThat(response.currentDayAverages().treatedPh()).isNull();
        assertThat(response.limeStockBags()).isNull();
        assertThat(response.limeConsumptionLastShiftBags()).isNull();
        assertThat(response.latestRawPh()).isNull();
        assertThat(response.latestTreatedPh()).isNull();

        StationDashboardResponse stationDashboard = response.stations().get(0);
        assertThat(stationDashboard.latestFlow().value()).isEqualByComparingTo("150.00");
        assertThat(stationDashboard.limeStockBags()).isNull();
        assertThat(stationDashboard.limeConsumptionLastShiftBags()).isNull();
        assertThat(stationDashboard.latestRawPh()).isNull();
        assertThat(stationDashboard.latestTreatedPh()).isNull();

        verify(context.limeService(), never()).currentStock(any(Station.class), any(Instant.class));
        verify(context.limeService(), never()).sumConsumption(any(Station.class), any(Instant.class), any(Instant.class));
        verify(context.phMeasurementRepository(), never())
                .findTopByStationInAndTypeAndDeletedFalseOrderByMeasuredAtDesc(anyCollection(), any());
        verify(context.phMeasurementRepository(), never())
                .findDashboardRows(any(), anyCollection(), any(Instant.class), any(Instant.class));
        verify(context.limeRecordRepository(), never())
                .findForList(anyCollection(), any(), any(Instant.class), any(Instant.class), any());
    }

    private void stubPrunableData(
            DashboardServiceTestContext context,
            Station station,
            AppUser author,
            Instant now
    ) {
        when(context.limeService().currentStock(any(Station.class), any(Instant.class)))
                .thenAnswer(invocation -> new LimeStockResponse(
                        station.getId(),
                        station.getName(),
                        new BigDecimal("12.00"),
                        invocation.getArgument(1)
                ));
        when(context.phMeasurementRepository().findTopByStationInAndTypeAndDeletedFalseOrderByMeasuredAtDesc(
                anyCollection(),
                eq(PhWaterType.RAW)
        )).thenReturn(Optional.of(phMeasurement(station, PhWaterType.RAW, "6.00", now, author)));
        when(context.phMeasurementRepository().findTopByStationInAndTypeAndDeletedFalseOrderByMeasuredAtDesc(
                anyCollection(),
                eq(PhWaterType.TREATED)
        )).thenReturn(Optional.of(phMeasurement(station, PhWaterType.TREATED, "7.00", now, author)));
        when(context.flowMeasurementRepository().findTopByStationInAndDeletedFalseOrderByMeasuredAtDesc(anyCollection()))
                .thenReturn(Optional.of(flowMeasurement(station, "150.00", now, author)));
        when(context.limeRecordRepository().findDashboardRows(
                eq(LimeRecordType.CONSUMPTION),
                anyCollection(),
                any(Instant.class),
                any(Instant.class)
        )).thenReturn(List.of(limeRecord(station, "3.00", now, author)));
        when(context.limeRecordRepository().findForList(
                anyCollection(),
                isNull(),
                any(Instant.class),
                any(Instant.class),
                isNull()
        )).thenReturn(List.of(limeRecord(station, "3.00", now, author)));
        when(context.stoppageRepository().findRecentForActivity(
                anyCollection(),
                any(Instant.class),
                any(Instant.class)
        )).thenReturn(List.of(stoppage(station, now.minusSeconds(3600), now.minusSeconds(1800), author)));
    }

    private void assertFullDashboard(DashboardResponse response) {
        assertThat(response.limeStockBags()).isEqualByComparingTo("12.00");
        assertThat(response.series()).isNotNull();
        assertThat(response.series().lime().get(0).points()).hasSize(1);
        assertThat(response.activity()).isNotNull();
        assertThat(response.activity().limeLogs()).hasSize(1);
        assertThat(response.activity().stoppageLogs()).hasSize(1);
        assertThat(response.latestRawPh().value()).isEqualByComparingTo("6.00");
        assertThat(response.latestTreatedPh().value()).isEqualByComparingTo("7.00");
        assertThat(response.latestFlow().value()).isEqualByComparingTo("150.00");

        StationDashboardResponse stationDashboard = response.stations().get(0);
        assertThat(stationDashboard.limeStockBags()).isEqualByComparingTo("12.00");
        assertThat(stationDashboard.latestRawPh().value()).isEqualByComparingTo("6.00");
        assertThat(stationDashboard.latestTreatedPh().value()).isEqualByComparingTo("7.00");
        assertThat(stationDashboard.latestFlow().value()).isEqualByComparingTo("150.00");
    }

    @Test
    void dashboardReturnsPeriodSeriesAndCurrentDayAverages() {
        Station station = station(UUID.randomUUID(), "ITE A");
        AppUser supervisor = user(UserRole.SUPERVISOR, station);
        DashboardServiceTestContext context = context(List.of(station), supervisor);
        Instant startAt = Instant.parse("2026-06-24T00:00:00Z");
        Instant endAt = Instant.parse("2026-07-01T00:00:00Z");

        when(context.phMeasurementRepository().findDashboardRows(
                eq(PhWaterType.RAW),
                anyCollection(),
                any(Instant.class),
                any(Instant.class)
        )).thenReturn(List.of(
                phMeasurement(station, PhWaterType.RAW, "6.00", startAt.plusSeconds(60), supervisor),
                phMeasurement(station, PhWaterType.RAW, "8.00", startAt.plusSeconds(120), supervisor)
        ));
        when(context.flowMeasurementRepository().findDashboardRows(
                anyCollection(),
                any(Instant.class),
                any(Instant.class)
        )).thenReturn(List.of(
                flowMeasurement(station, "100.00", startAt.plusSeconds(60), supervisor),
                flowMeasurement(station, "200.00", startAt.plusSeconds(120), supervisor)
        ));
        when(context.limeRecordRepository().findDashboardRows(
                eq(LimeRecordType.CONSUMPTION),
                anyCollection(),
                any(Instant.class),
                any(Instant.class)
        )).thenReturn(List.of(limeRecord(station, "3.00", startAt.plusSeconds(180), supervisor)));

        DashboardResponse response = context.service()
                .dashboard(station.getId(), false, startAt, endAt, UserPrincipal.from(supervisor));

        assertThat(response.period()).isEqualTo(new DashboardPeriodResponse(startAt, endAt));
        assertThat(response.currentDayAverages().rawPh().value()).isEqualByComparingTo("7.00");
        assertThat(response.currentDayAverages().flow().value()).isEqualByComparingTo("150.00");
        assertThat(response.currentDayAverages().workedHoursToday()).isNotNull();
        assertThat(response.series().rawPh()).hasSize(1);
        assertThat(response.series().rawPh().get(0).points()).hasSize(2);
        assertThat(response.series().flow().get(0).points()).hasSize(2);
        assertThat(response.series().lime().get(0).points()).hasSize(1);
    }

    @Test
    void currentDayAveragesStartAtSaoPauloMidnightAndExcludePreviousDay() {
        Station station = station(UUID.randomUUID(), "ITE A");
        AppUser supervisor = user(UserRole.SUPERVISOR, station);
        Instant now = Instant.parse("2026-07-07T13:00:00Z");
        Instant startOfDay = Instant.parse("2026-07-07T03:00:00Z");
        DashboardServiceTestContext context = context(List.of(station), supervisor, now);

        when(context.phMeasurementRepository().findDashboardRows(
                eq(PhWaterType.RAW),
                anyCollection(),
                eq(startOfDay),
                eq(now)
        )).thenReturn(List.of(
                phMeasurement(station, PhWaterType.RAW, "6.00", startOfDay, supervisor),
                phMeasurement(station, PhWaterType.RAW, "8.00", now.minusSeconds(60), supervisor)
        ));
        when(context.flowMeasurementRepository().findDashboardRows(anyCollection(), eq(startOfDay), eq(now)))
                .thenReturn(List.of(
                        flowMeasurement(station, "100.00", startOfDay, supervisor),
                        flowMeasurement(station, "200.00", now.minusSeconds(60), supervisor),
                        flowMeasurement(station, "300.00", now.minusSeconds(30), supervisor)
                ));

        DashboardResponse response = context.service()
                .dashboard(station.getId(), false, UserPrincipal.from(supervisor));

        assertThat(response.currentDayAverages().startAt()).isEqualTo(startOfDay);
        assertThat(response.currentDayAverages().endAt()).isEqualTo(now);
        assertThat(response.currentDayAverages().rawPh().startAt()).isEqualTo(startOfDay);
        assertThat(response.currentDayAverages().rawPh().endAt()).isEqualTo(now);
        assertThat(response.currentDayAverages().rawPh().value()).isEqualByComparingTo("7.00");
        assertThat(response.currentDayAverages().flowAverageToday()).isEqualByComparingTo("200.00");
        assertThat(response.currentDayAverages().flowTotalToday()).isEqualByComparingTo("600.00");
    }

    @Test
    void workedHoursSubtractsOnlyStoppageOverlapWithCurrentDay() {
        Station station = station(UUID.randomUUID(), "ITE A");
        AppUser supervisor = user(UserRole.SUPERVISOR, station);
        Instant now = Instant.parse("2026-07-07T13:00:00Z");
        Instant startOfDay = Instant.parse("2026-07-07T03:00:00Z");
        DashboardServiceTestContext context = context(List.of(station), supervisor, now);
        Stoppage beforeMidnight = stoppage(
                station,
                Instant.parse("2026-07-07T02:00:00Z"),
                Instant.parse("2026-07-07T04:00:00Z"),
                supervisor
        );
        Stoppage duringMorning = stoppage(
                station,
                Instant.parse("2026-07-07T11:00:00Z"),
                Instant.parse("2026-07-07T12:30:00Z"),
                supervisor
        );

        when(context.stoppageRepository().findOverlapping(eq(startOfDay), eq(now), nullable(UUID.class)))
                .thenReturn(List.of(beforeMidnight, duringMorning));

        DashboardResponse response = context.service()
                .dashboard(station.getId(), false, UserPrincipal.from(supervisor));

        assertThat(response.currentDayAverages().workedHoursToday()).isEqualByComparingTo("7.50");
    }

    @Test
    void currentDayAveragesIgnoreDeletedMeasurements() {
        Station station = station(UUID.randomUUID(), "ITE A");
        AppUser supervisor = user(UserRole.SUPERVISOR, station);
        Instant now = Instant.parse("2026-07-07T13:00:00Z");
        Instant startOfDay = Instant.parse("2026-07-07T03:00:00Z");
        DashboardServiceTestContext context = context(List.of(station), supervisor, now);
        PhMeasurement deletedPh = phMeasurement(station, PhWaterType.RAW, "10.00", startOfDay.plusSeconds(60), supervisor);
        FlowMeasurement deletedFlow = flowMeasurement(station, "900.00", startOfDay.plusSeconds(60), supervisor);
        deletedPh.softDelete(supervisor);
        deletedFlow.softDelete(supervisor);

        when(context.phMeasurementRepository().findDashboardRows(
                eq(PhWaterType.RAW),
                anyCollection(),
                eq(startOfDay),
                eq(now)
        )).thenReturn(List.of(
                phMeasurement(station, PhWaterType.RAW, "6.00", startOfDay, supervisor),
                deletedPh
        ));
        when(context.flowMeasurementRepository().findDashboardRows(anyCollection(), eq(startOfDay), eq(now)))
                .thenReturn(List.of(
                        flowMeasurement(station, "100.00", startOfDay, supervisor),
                        deletedFlow
                ));

        DashboardResponse response = context.service()
                .dashboard(station.getId(), false, UserPrincipal.from(supervisor));

        assertThat(response.currentDayAverages().rawPh().value()).isEqualByComparingTo("6.00");
        assertThat(response.currentDayAverages().rawPh().sampleCount()).isEqualTo(1);
        assertThat(response.currentDayAverages().flowAverageToday()).isEqualByComparingTo("100.00");
        assertThat(response.currentDayAverages().flowTotalToday()).isEqualByComparingTo("100.00");
    }

    @Test
    void flowAveragePerWorkedHourReturnsZeroWhenWorkedHoursIsZero() {
        Station station = station(UUID.randomUUID(), "ITE A");
        AppUser supervisor = user(UserRole.SUPERVISOR, station);
        Instant now = Instant.parse("2026-07-07T03:00:00Z");
        DashboardServiceTestContext context = context(List.of(station), supervisor, now);

        when(context.flowMeasurementRepository().findDashboardRows(anyCollection(), eq(now), eq(now)))
                .thenReturn(List.of(flowMeasurement(station, "100.00", now, supervisor)));

        DashboardResponse response = context.service()
                .dashboard(station.getId(), false, UserPrincipal.from(supervisor));

        assertThat(response.currentDayAverages().workedHoursToday()).isEqualByComparingTo("0.00");
        assertThat(response.currentDayAverages().flowAveragePerWorkedHour()).isEqualByComparingTo("0.00");
    }

    @Test
    void flowTotalTodayUsesExactMeasurementSum() {
        Station station = station(UUID.randomUUID(), "ITE A");
        AppUser supervisor = user(UserRole.SUPERVISOR, station);
        Instant now = Instant.parse("2026-07-07T12:00:00Z");
        Instant startOfDay = Instant.parse("2026-07-07T03:00:00Z");
        DashboardServiceTestContext context = context(List.of(station), supervisor, now);

        when(context.flowMeasurementRepository().findDashboardRows(anyCollection(), eq(startOfDay), eq(now)))
                .thenReturn(List.of(
                        flowMeasurement(station, "1.00", startOfDay, supervisor),
                        flowMeasurement(station, "1.00", Instant.parse("2026-07-07T04:00:00Z"), supervisor),
                        flowMeasurement(station, "2.00", Instant.parse("2026-07-07T05:00:00Z"), supervisor)
                ));

        DashboardResponse response = context.service()
                .dashboard(station.getId(), false, UserPrincipal.from(supervisor));

        assertThat(response.currentDayAverages().flowAverageToday()).isEqualByComparingTo("1.333333333333333");
        assertThat(response.currentDayAverages().flowTotalToday()).isEqualByComparingTo("4.00");
    }

    @Test
    void perStationWorkedHoursTodaySubtractsOnlyOwnStationStoppages() {
        Station stationA = station(UUID.randomUUID(), "ITE A");
        Station stationB = station(UUID.randomUUID(), "ITE B");
        AppUser supervisor = user(UserRole.SUPERVISOR, stationA);
        Instant now = Instant.parse("2026-07-07T13:00:00Z");
        Instant startOfDay = Instant.parse("2026-07-07T03:00:00Z");
        DashboardServiceTestContext context = context(List.of(stationA, stationB), supervisor, now);
        Stoppage stationADowntime = stoppage(
                stationA,
                Instant.parse("2026-07-07T11:00:00Z"),
                Instant.parse("2026-07-07T12:30:00Z"),
                user(UserRole.OPERATOR, stationA)
        );
        when(context.stoppageRepository().findOverlapping(eq(startOfDay), eq(now), nullable(UUID.class)))
                .thenReturn(List.of(stationADowntime));

        DashboardResponse response = context.service().dashboard(null, true, UserPrincipal.from(supervisor));

        assertThat(stationWorkedHours(response, stationA.getId())).isEqualByComparingTo("8.50");
        assertThat(stationWorkedHours(response, stationB.getId())).isEqualByComparingTo("10.00");
    }

    @Test
    void activityLogsIncludeAllLimeMovementTypesAndStoppages() {
        Station station = station(UUID.randomUUID(), "ITE A");
        AppUser supervisor = user(UserRole.SUPERVISOR, station);
        DashboardServiceTestContext context = context(List.of(station), supervisor);
        Instant startAt = Instant.parse("2026-06-24T00:00:00Z");
        Instant endAt = Instant.parse("2026-07-01T00:00:00Z");
        LimeRecord entry = new LimeRecord(
                station,
                LimeRecordType.ENTRY,
                new BigDecimal("10.00"),
                startAt.plusSeconds(60),
                null,
                supervisor
        );
        LimeRecord consumption = new LimeRecord(
                station,
                LimeRecordType.CONSUMPTION,
                new BigDecimal("5.00"),
                startAt.plusSeconds(120),
                null,
                supervisor
        );
        Stoppage finished = stoppage(
                station,
                startAt.plusSeconds(200),
                startAt.plusSeconds(8300),
                supervisor
        );
        when(context.limeRecordRepository().findForList(anyCollection(), isNull(), any(Instant.class), any(Instant.class), isNull()))
                .thenReturn(List.of(entry, consumption));
        when(context.stoppageRepository().findRecentForActivity(anyCollection(), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(finished));

        DashboardResponse response = context.service()
                .dashboard(station.getId(), false, startAt, endAt, UserPrincipal.from(supervisor));

        assertThat(response.activity().limeLogs())
                .extracting(DashboardLimeLogResponse::type)
                .containsExactlyInAnyOrder(LimeRecordType.ENTRY, LimeRecordType.CONSUMPTION);
        assertThat(response.activity().stoppageLogs())
                .extracting(DashboardStoppageLogResponse::status)
                .containsExactly(StoppageStatus.FINISHED);
    }

    @Test
    void dashboardRejectsInvalidPeriod() {
        Station station = station(UUID.randomUUID(), "ITE A");
        AppUser supervisor = user(UserRole.SUPERVISOR, station);
        DashboardService service = context(List.of(station), supervisor).service();
        Instant startAt = Instant.parse("2026-07-01T00:00:00Z");
        Instant endAt = Instant.parse("2026-06-30T00:00:00Z");

        assertThatThrownBy(() -> service.dashboard(station.getId(), false, startAt, endAt, UserPrincipal.from(supervisor)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Horário de fim deve ser maior que o horário de início");
    }

    private DashboardServiceTestContext context(List<Station> stations, AppUser currentUser) {
        return context(stations, currentUser, Instant.parse("2026-07-01T12:00:00Z"));
    }

    private DashboardServiceTestContext context(List<Station> stations, AppUser currentUser, Instant now) {
        LimeService limeService = mock(LimeService.class);
        LimeRecordRepository limeRecordRepository = mock(LimeRecordRepository.class);
        PhMeasurementRepository phMeasurementRepository = mock(PhMeasurementRepository.class);
        FlowMeasurementRepository flowMeasurementRepository = mock(FlowMeasurementRepository.class);
        StoppageRepository stoppageRepository = mock(StoppageRepository.class);
        ShiftCalculator shiftCalculator = mock(ShiftCalculator.class);
        StationService stationService = new FakeStationService(stations);
        StationScopeService stationScopeService = new StationScopeService(
                stationService,
                new FakeCurrentUserService(currentUser)
        );

        when(shiftCalculator.lastCompletedShiftWindow(any(Instant.class)))
                .thenReturn(new ShiftWindow(
                        LocalDate.of(2026, 7, 1),
                        ShiftType.DAY,
                        Instant.parse("2026-07-01T10:00:00Z"),
                        Instant.parse("2026-07-01T22:00:00Z")
                ));
        when(limeService.currentStock(any(Station.class), any(Instant.class)))
                .thenAnswer(invocation -> {
                    Station station = invocation.getArgument(0);
                    Instant asOf = invocation.getArgument(1);
                    return new LimeStockResponse(station.getId(), station.getName(), BigDecimal.ZERO, asOf);
                });
        when(limeService.sumConsumption(any(Station.class), any(Instant.class), any(Instant.class)))
                .thenReturn(BigDecimal.ZERO);
        when(phMeasurementRepository.findTopByStationInAndTypeAndDeletedFalseOrderByMeasuredAtDesc(anyCollection(), any()))
                .thenReturn(Optional.empty());
        when(phMeasurementRepository.findDashboardRows(any(), anyCollection(), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of());
        when(flowMeasurementRepository.findTopByStationInAndDeletedFalseOrderByMeasuredAtDesc(anyCollection()))
                .thenReturn(Optional.empty());
        when(flowMeasurementRepository.findDashboardRows(anyCollection(), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of());
        when(limeRecordRepository.findDashboardRows(any(), anyCollection(), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of());
        when(stoppageRepository.findAllByStationInAndStatusAndDeletedFalse(anyCollection(), any(StoppageStatus.class)))
                .thenReturn(List.of());
        when(stoppageRepository.findOverlapping(any(Instant.class), any(Instant.class), nullable(UUID.class)))
                .thenReturn(List.of());

        DashboardService service = new DashboardService(
                stationService,
                limeService,
                limeRecordRepository,
                phMeasurementRepository,
                flowMeasurementRepository,
                stoppageRepository,
                shiftCalculator,
                stationScopeService,
                Clock.fixed(now, ZoneId.of("America/Sao_Paulo"))
        );
        return new DashboardServiceTestContext(
                service,
                limeService,
                limeRecordRepository,
                phMeasurementRepository,
                flowMeasurementRepository,
                stoppageRepository
        );
    }

    private BigDecimal stationWorkedHours(DashboardResponse response, UUID stationId) {
        return response.stations().stream()
                .filter(station -> station.stationId().equals(stationId))
                .map(StationDashboardResponse::workedHoursToday)
                .findFirst()
                .orElseThrow();
    }

    private AppUser user(UserRole role, Station station) {
        return new AppUser("User", role.name().toLowerCase(), "hash", role, station, false);
    }

    private Station station(UUID stationId, String name) {
        Station station = new Station(name, name.toLowerCase().replace(" ", "-"));
        ReflectionTestUtils.setField(station, "id", stationId);
        return station;
    }

    private PhMeasurement phMeasurement(
            Station station,
            PhWaterType type,
            String ph,
            Instant measuredAt,
            AppUser createdBy
    ) {
        return new PhMeasurement(station, type, new BigDecimal(ph), measuredAt, createdBy);
    }

    private FlowMeasurement flowMeasurement(Station station, String flowLiters, Instant measuredAt, AppUser createdBy) {
        return new FlowMeasurement(station, new BigDecimal(flowLiters), measuredAt, createdBy);
    }

    private LimeRecord limeRecord(Station station, String quantityBags, Instant recordedAt, AppUser createdBy) {
        return new LimeRecord(
                station,
                LimeRecordType.CONSUMPTION,
                new BigDecimal(quantityBags),
                recordedAt,
                null,
                createdBy
        );
    }

    private Stoppage stoppage(Station station, Instant startAt, Instant endAt, AppUser createdBy) {
        Stoppage stoppage = new Stoppage(station, StoppageReason.OUTROS, startAt, createdBy);
        stoppage.finish(endAt);
        return stoppage;
    }

    private record DashboardServiceTestContext(
            DashboardService service,
            LimeService limeService,
            LimeRecordRepository limeRecordRepository,
            PhMeasurementRepository phMeasurementRepository,
            FlowMeasurementRepository flowMeasurementRepository,
            StoppageRepository stoppageRepository
    ) {
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
