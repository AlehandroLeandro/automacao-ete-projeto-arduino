package br.com.carbonifera.ete.dashboard;

import br.com.carbonifera.ete.lime.LimeRecord;
import br.com.carbonifera.ete.lime.LimeRecordRepository;
import br.com.carbonifera.ete.lime.LimeRecordType;
import br.com.carbonifera.ete.lime.LimeService;
import br.com.carbonifera.ete.measurement.FlowMeasurement;
import br.com.carbonifera.ete.measurement.FlowMeasurementRepository;
import br.com.carbonifera.ete.measurement.PhMeasurement;
import br.com.carbonifera.ete.measurement.PhMeasurementRepository;
import br.com.carbonifera.ete.measurement.PhWaterType;
import br.com.carbonifera.ete.security.UserPrincipal;
import br.com.carbonifera.ete.station.Station;
import br.com.carbonifera.ete.station.StationScope;
import br.com.carbonifera.ete.station.StationScopeService;
import br.com.carbonifera.ete.station.StationService;
import br.com.carbonifera.ete.stoppage.ShiftCalculator;
import br.com.carbonifera.ete.stoppage.ShiftWindow;
import br.com.carbonifera.ete.stoppage.Stoppage;
import br.com.carbonifera.ete.stoppage.StoppageRepository;
import br.com.carbonifera.ete.stoppage.StoppageStatus;
import br.com.carbonifera.ete.user.UserRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class DashboardService {

    private static final Duration DEFAULT_SERIES_PERIOD = Duration.ofDays(7);
    private static final MathContext AVERAGE_CONTEXT = MathContext.DECIMAL64;
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("America/Sao_Paulo");
    private static final int MAX_ACTIVITY_ITEMS = 10;

    private final StationService stationService;
    private final LimeService limeService;
    private final LimeRecordRepository limeRecordRepository;
    private final PhMeasurementRepository phMeasurementRepository;
    private final FlowMeasurementRepository flowMeasurementRepository;
    private final StoppageRepository stoppageRepository;
    private final ShiftCalculator shiftCalculator;
    private final StationScopeService stationScopeService;
    private final Clock clock;

    @Autowired
    public DashboardService(
            StationService stationService,
            LimeService limeService,
            LimeRecordRepository limeRecordRepository,
            PhMeasurementRepository phMeasurementRepository,
            FlowMeasurementRepository flowMeasurementRepository,
            StoppageRepository stoppageRepository,
            ShiftCalculator shiftCalculator,
            StationScopeService stationScopeService
    ) {
        this(
                stationService,
                limeService,
                limeRecordRepository,
                phMeasurementRepository,
                flowMeasurementRepository,
                stoppageRepository,
                shiftCalculator,
                stationScopeService,
                Clock.system(BUSINESS_ZONE)
        );
    }

    DashboardService(
            StationService stationService,
            LimeService limeService,
            LimeRecordRepository limeRecordRepository,
            PhMeasurementRepository phMeasurementRepository,
            FlowMeasurementRepository flowMeasurementRepository,
            StoppageRepository stoppageRepository,
            ShiftCalculator shiftCalculator,
            StationScopeService stationScopeService,
            Clock clock
    ) {
        this.stationService = stationService;
        this.limeService = limeService;
        this.limeRecordRepository = limeRecordRepository;
        this.phMeasurementRepository = phMeasurementRepository;
        this.flowMeasurementRepository = flowMeasurementRepository;
        this.stoppageRepository = stoppageRepository;
        this.shiftCalculator = shiftCalculator;
        this.stationScopeService = stationScopeService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public DashboardResponse dashboard(UUID stationId, boolean consolidated, UserPrincipal principal) {
        return dashboard(stationId, consolidated, null, null, principal);
    }

    @Transactional(readOnly = true)
    public DashboardResponse dashboard(
            UUID stationId,
            boolean consolidated,
            Instant startAt,
            Instant endAt,
            UserPrincipal principal
    ) {
        StationScope scope = resolveScope(stationId, consolidated, principal);
        DashboardView view = DashboardView.of(principal.getRole());
        boolean full = view == DashboardView.FULL;
        Instant now = clock.instant();
        DashboardPeriodResponse period = resolvePeriod(startAt, endAt, now);
        List<Station> stations = stationService.resolveStations(scope.stationId(), scope.consolidated());
        List<StationDashboardResponse> stationDashboards = stations.stream()
                .map(station -> stationDashboard(station, now, view))
                .toList();

        BigDecimal limeStock = full
                ? stationDashboards.stream()
                        .map(StationDashboardResponse::limeStockBags)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                : null;
        BigDecimal limeConsumptionLastShift = view.showsPhAndLime()
                ? stationDashboards.stream()
                        .map(StationDashboardResponse::limeConsumptionLastShiftBags)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                : null;
        DashboardCurrentDayAveragesResponse averages = currentDayAverages(stations, now, view.showsPhAndLime());
        DashboardSeriesCollectionResponse series = view.showsTrends() ? series(stations, period, view.showsPhAndLime()) : null;
        DashboardActivityResponse activity = view.showsTrends() ? activity(stations, period, now, view.showsPhAndLime()) : null;

        return new DashboardResponse(
                scope.consolidated() || stations.size() > 1,
                scope.stationId(),
                now,
                period,
                limeStock,
                limeConsumptionLastShift,
                averages,
                series,
                activity,
                full ? latestRawPh(stations) : null,
                full ? latestTreatedPh(stations) : null,
                view.showsTrends() ? latestFlow(stations) : null,
                operation(stations, now),
                stationDashboards
        );
    }

    private DashboardPeriodResponse resolvePeriod(Instant startAt, Instant endAt, Instant now) {
        if (startAt == null && endAt == null) {
            return new DashboardPeriodResponse(now.minus(DEFAULT_SERIES_PERIOD), now);
        }
        if (startAt == null || endAt == null) {
            throw new IllegalArgumentException("Both startAt and endAt must be provided");
        }
        if (!endAt.isAfter(startAt)) {
            throw new IllegalArgumentException("Horário de fim deve ser maior que o horário de início");
        }
        return new DashboardPeriodResponse(startAt, endAt);
    }

    private StationScope resolveScope(UUID stationId, boolean consolidated, UserPrincipal principal) {
        return stationScopeService.resolveDashboardScope(stationId, consolidated, requirePrincipal(principal));
    }

    private UserPrincipal requirePrincipal(UserPrincipal principal) {
        return Optional.ofNullable(principal)
                .orElseThrow(() -> new AuthenticationCredentialsNotFoundException("Authenticated principal is required"));
    }

    private StationDashboardResponse stationDashboard(Station station, Instant now, DashboardView view) {
        boolean full = view == DashboardView.FULL;
        ShiftWindow lastShift = shiftCalculator.lastCompletedShiftWindow(now);
        BigDecimal lastShiftConsumption = view.showsPhAndLime()
                ? limeService.sumConsumption(station, lastShift.startAt(), lastShift.endAt())
                : null;
        List<Station> singleStation = List.of(station);
        BigDecimal workedHoursToday = workedHoursToday(singleStation, startOfBusinessDay(now), now);

        return new StationDashboardResponse(
                station.getId(),
                station.getName(),
                full ? limeService.currentStock(station, now).currentStockBags() : null,
                lastShiftConsumption,
                full ? latestRawPh(singleStation) : null,
                full ? latestTreatedPh(singleStation) : null,
                view.showsTrends() ? latestFlow(singleStation) : null,
                operation(singleStation, now),
                workedHoursToday
        );
    }

    private DashboardMeasurementResponse latestRawPh(List<Station> stations) {
        return phMeasurementRepository.findTopByStationInAndTypeAndDeletedFalseOrderByMeasuredAtDesc(stations, PhWaterType.RAW)
                .map(this::phResponse)
                .orElse(null);
    }

    private DashboardMeasurementResponse latestTreatedPh(List<Station> stations) {
        return phMeasurementRepository.findTopByStationInAndTypeAndDeletedFalseOrderByMeasuredAtDesc(stations, PhWaterType.TREATED)
                .map(this::phResponse)
                .orElse(null);
    }

    private DashboardMeasurementResponse latestFlow(List<Station> stations) {
        return flowMeasurementRepository.findTopByStationInAndDeletedFalseOrderByMeasuredAtDesc(stations)
                .map(this::flowResponse)
                .orElse(null);
    }

    private DashboardCurrentDayAveragesResponse currentDayAverages(List<Station> stations, Instant now, boolean includePh) {
        Instant startAt = startOfBusinessDay(now);
        BigDecimal workedHoursToday = workedHoursToday(stations, startAt, now);
        FlowAverages flowAverages = flowAverages(stations, startAt, now);

        return new DashboardCurrentDayAveragesResponse(
                startAt,
                now,
                includePh ? phAverage(stations, PhWaterType.RAW, startAt, now) : null,
                includePh ? phAverage(stations, PhWaterType.TREATED, startAt, now) : null,
                flowAverages.measurementAverage(),
                workedHoursToday,
                flowAverages.average(),
                flowAverages.total(),
                divideOrZero(flowAverages.total(), workedHoursToday)
        );
    }

    private Instant startOfBusinessDay(Instant now) {
        return LocalDate.ofInstant(now, BUSINESS_ZONE).atStartOfDay(BUSINESS_ZONE).toInstant();
    }

    private BigDecimal workedHoursToday(List<Station> stations, Instant startAt, Instant now) {
        Duration period = Duration.between(startAt, now).multipliedBy(stations.size());
        Duration downtime = stoppageRepository.findOverlapping(startAt, now, null)
                .stream()
                .filter(stoppage -> containsStation(stations, stoppage.getStation()))
                .map(stoppage -> overlapDuration(stoppage, startAt, now))
                .reduce(Duration.ZERO, Duration::plus);
        return hours(period.minus(downtime));
    }

    private Duration overlapDuration(Stoppage stoppage, Instant startAt, Instant endAt) {
        Instant segmentStart = stoppage.getStartAt().isAfter(startAt) ? stoppage.getStartAt() : startAt;
        Instant segmentEnd = stoppage.getEndAt() == null || stoppage.getEndAt().isAfter(endAt)
                ? endAt
                : stoppage.getEndAt();
        if (!segmentEnd.isAfter(segmentStart)) {
            return Duration.ZERO;
        }
        return Duration.between(segmentStart, segmentEnd);
    }

    private boolean containsStation(List<Station> stations, Station station) {
        return stations.stream().anyMatch(candidate -> sameStation(candidate, station));
    }

    private BigDecimal hours(Duration duration) {
        return BigDecimal.valueOf(Math.max(0, duration.toSeconds()))
                .divide(BigDecimal.valueOf(3600), 2, RoundingMode.HALF_UP);
    }

    private DashboardMeasurementAverageResponse phAverage(
            List<Station> stations,
            PhWaterType type,
            Instant startAt,
            Instant endAt
    ) {
        List<BigDecimal> values = phMeasurementRepository.findDashboardRows(type, stations, startAt, endAt)
                .stream()
                .filter(measurement -> !measurement.isDeleted())
                .map(PhMeasurement::getPh)
                .toList();
        return average(values, "pH", startAt, endAt);
    }

    private FlowAverages flowAverages(List<Station> stations, Instant startAt, Instant endAt) {
        List<BigDecimal> values = flowMeasurementRepository.findDashboardRows(stations, startAt, endAt)
                .stream()
                .filter(measurement -> !measurement.isDeleted())
                .map(FlowMeasurement::getFlowLiters)
                .toList();
        BigDecimal total = sum(values);
        BigDecimal average = averageValue(values, total);
        DashboardMeasurementAverageResponse measurementAverage = average(values, "L", startAt, endAt);
        return new FlowAverages(measurementAverage, average, total);
    }

    private DashboardMeasurementAverageResponse average(
            List<BigDecimal> values,
            String unit,
            Instant startAt,
            Instant endAt
    ) {
        if (values.isEmpty()) {
            return null;
        }
        BigDecimal value = averageValue(values, sum(values));
        return new DashboardMeasurementAverageResponse(value, unit, values.size(), startAt, endAt);
    }

    private BigDecimal averageValue(List<BigDecimal> values, BigDecimal sum) {
        if (values.isEmpty()) {
            return BigDecimal.ZERO;
        }
        return sum.divide(BigDecimal.valueOf(values.size()), AVERAGE_CONTEXT);
    }

    private BigDecimal sum(List<BigDecimal> values) {
        return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal divideOrZero(BigDecimal dividend, BigDecimal divisor) {
        if (divisor.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return dividend.divide(divisor, 2, RoundingMode.HALF_UP);
    }

    private DashboardActivityResponse activity(
            List<Station> stations,
            DashboardPeriodResponse period,
            Instant now,
            boolean includeLime
    ) {
        List<DashboardLimeLogResponse> limeLogs = includeLime
                ? limeRecordRepository
                        .findForList(stations, null, period.startAt(), period.endAt(), null)
                        .stream()
                        .sorted(Comparator.comparing(LimeRecord::getRecordedAt).reversed())
                        .limit(MAX_ACTIVITY_ITEMS)
                        .map(DashboardLimeLogResponse::from)
                        .toList()
                : List.of();

        List<DashboardStoppageLogResponse> stoppageLogs = stoppageRepository
                .findRecentForActivity(stations, period.startAt(), period.endAt())
                .stream()
                .limit(MAX_ACTIVITY_ITEMS)
                .map(stoppage -> DashboardStoppageLogResponse.from(stoppage, now))
                .toList();

        return new DashboardActivityResponse(limeLogs, stoppageLogs);
    }

    private DashboardSeriesCollectionResponse series(
            List<Station> stations,
            DashboardPeriodResponse period,
            boolean includePhAndLime
    ) {
        if (!includePhAndLime) {
            return new DashboardSeriesCollectionResponse(flowSeries(stations, period), List.of(), List.of(), List.of());
        }
        return new DashboardSeriesCollectionResponse(
                flowSeries(stations, period),
                phSeries(stations, PhWaterType.RAW, DashboardMetric.RAW_PH, period),
                phSeries(stations, PhWaterType.TREATED, DashboardMetric.TREATED_PH, period),
                limeSeries(stations, period)
        );
    }

    private List<DashboardSeriesResponse> phSeries(
            List<Station> stations,
            PhWaterType type,
            DashboardMetric metric,
            DashboardPeriodResponse period
    ) {
        List<PhMeasurement> measurements = phMeasurementRepository.findDashboardRows(
                type,
                stations,
                period.startAt(),
                period.endAt()
        );
        return stations.stream()
                .map(station -> phSeries(station, metric, measurements))
                .filter(series -> !series.points().isEmpty())
                .toList();
    }

    private DashboardSeriesResponse phSeries(
            Station station,
            DashboardMetric metric,
            List<PhMeasurement> measurements
    ) {
        List<DashboardSeriesPointResponse> points = measurements.stream()
                .filter(measurement -> sameStation(measurement.getStation(), station))
                .map(measurement -> new DashboardSeriesPointResponse(measurement.getMeasuredAt(), measurement.getPh()))
                .toList();
        return new DashboardSeriesResponse(station.getId(), station.getName(), metric, "pH", points);
    }

    private List<DashboardSeriesResponse> flowSeries(List<Station> stations, DashboardPeriodResponse period) {
        List<FlowMeasurement> measurements = flowMeasurementRepository.findDashboardRows(
                stations,
                period.startAt(),
                period.endAt()
        );
        return stations.stream()
                .map(station -> flowSeries(station, measurements))
                .filter(series -> !series.points().isEmpty())
                .toList();
    }

    private DashboardSeriesResponse flowSeries(Station station, List<FlowMeasurement> measurements) {
        List<DashboardSeriesPointResponse> points = measurements.stream()
                .filter(measurement -> sameStation(measurement.getStation(), station))
                .map(measurement -> new DashboardSeriesPointResponse(
                        measurement.getMeasuredAt(),
                        measurement.getFlowLiters()
                ))
                .toList();
        return new DashboardSeriesResponse(station.getId(), station.getName(), DashboardMetric.FLOW, "L", points);
    }

    private List<DashboardSeriesResponse> limeSeries(List<Station> stations, DashboardPeriodResponse period) {
        List<LimeRecord> records = limeRecordRepository.findDashboardRows(
                LimeRecordType.CONSUMPTION,
                stations,
                period.startAt(),
                period.endAt()
        );
        return stations.stream()
                .map(station -> limeSeries(station, records))
                .filter(series -> !series.points().isEmpty())
                .toList();
    }

    private DashboardSeriesResponse limeSeries(Station station, List<LimeRecord> records) {
        List<DashboardSeriesPointResponse> points = records.stream()
                .filter(record -> sameStation(record.getStation(), station))
                .map(record -> new DashboardSeriesPointResponse(record.getRecordedAt(), record.getQuantityBags()))
                .toList();
        return new DashboardSeriesResponse(station.getId(), station.getName(), DashboardMetric.LIME, "bags", points);
    }

    private boolean sameStation(Station firstStation, Station secondStation) {
        return firstStation.getId().equals(secondStation.getId());
    }

    private OperationalStatusResponse operation(List<Station> stations, Instant now) {
        List<DashboardStoppageResponse> activeStoppages = stoppageRepository
                .findAllByStationInAndStatusAndDeletedFalse(stations, StoppageStatus.ACTIVE)
                .stream()
                .map(stoppage -> DashboardStoppageResponse.from(stoppage, now))
                .toList();
        OperationStatus status = activeStoppages.isEmpty() ? OperationStatus.WORKING : OperationStatus.STOPPED;
        return new OperationalStatusResponse(status, activeStoppages);
    }

    private DashboardMeasurementResponse phResponse(PhMeasurement measurement) {
        return new DashboardMeasurementResponse(
                measurement.getStation().getId(),
                measurement.getStation().getName(),
                measurement.getPh(),
                "pH",
                measurement.getMeasuredAt()
        );
    }

    private DashboardMeasurementResponse flowResponse(FlowMeasurement measurement) {
        return new DashboardMeasurementResponse(
                measurement.getStation().getId(),
                measurement.getStation().getName(),
                measurement.getFlowLiters(),
                "L",
                measurement.getMeasuredAt()
        );
    }

    /**
     * FULL: encarregado/administrador. OPERATOR: status e médias do dia.
     * VIEWER: somente vazão e paradas, com gráfico e log de paradas.
     */
    private enum DashboardView {
        FULL,
        OPERATOR,
        VIEWER;

        static DashboardView of(UserRole role) {
            return switch (role) {
                case OPERATOR -> OPERATOR;
                case VIEWER -> VIEWER;
                default -> FULL;
            };
        }

        boolean showsTrends() {
            return this != OPERATOR;
        }

        boolean showsPhAndLime() {
            return this != VIEWER;
        }
    }

    private record FlowAverages(
            DashboardMeasurementAverageResponse measurementAverage,
            BigDecimal average,
            BigDecimal total
    ) {
    }
}
