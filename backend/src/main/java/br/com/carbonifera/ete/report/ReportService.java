package br.com.carbonifera.ete.report;

import br.com.carbonifera.ete.lime.LimeRecord;
import br.com.carbonifera.ete.lime.LimeRecordRepository;
import br.com.carbonifera.ete.lime.LimeRecordType;
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
import br.com.carbonifera.ete.stoppage.ShiftType;
import br.com.carbonifera.ete.stoppage.Stoppage;
import br.com.carbonifera.ete.stoppage.StoppageReasonLabels;
import br.com.carbonifera.ete.stoppage.StoppageService;
import br.com.carbonifera.ete.stoppage.StoppageStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ReportService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("America/Sao_Paulo");
    private static final MathContext AVERAGE_CONTEXT = MathContext.DECIMAL64;

    private final PhMeasurementRepository phMeasurementRepository;
    private final FlowMeasurementRepository flowMeasurementRepository;
    private final LimeRecordRepository limeRecordRepository;
    private final StoppageService stoppageService;
    private final StationService stationService;
    private final StationScopeService stationScopeService;
    private final ShiftCalculator shiftCalculator;

    public ReportService(
            PhMeasurementRepository phMeasurementRepository,
            FlowMeasurementRepository flowMeasurementRepository,
            LimeRecordRepository limeRecordRepository,
            StoppageService stoppageService,
            StationService stationService,
            StationScopeService stationScopeService,
            ShiftCalculator shiftCalculator
    ) {
        this.phMeasurementRepository = phMeasurementRepository;
        this.flowMeasurementRepository = flowMeasurementRepository;
        this.limeRecordRepository = limeRecordRepository;
        this.stoppageService = stoppageService;
        this.stationService = stationService;
        this.stationScopeService = stationScopeService;
        this.shiftCalculator = shiftCalculator;
    }

    @Transactional(readOnly = true)
    public ReportResponse generate(
            ReportType type,
            Instant startAt,
            Instant endAt,
            UUID stationId,
            boolean consolidated,
            UserPrincipal principal
    ) {
        StationScope scope = stationScopeService.resolveReportScope(stationId, consolidated, principal);
        return generate(type, startAt, endAt, scope);
    }

    @Transactional(readOnly = true)
    public ReportResponse generate(ReportType type, Instant startAt, Instant endAt, UUID stationId, boolean consolidated) {
        return generate(type, startAt, endAt, new StationScope(stationId, consolidated));
    }

    private ReportResponse generate(
            ReportType type,
            Instant startAt,
            Instant endAt,
            StationScope scope
    ) {
        if (!endAt.isAfter(startAt)) {
            throw new IllegalArgumentException("Horário de fim deve ser maior que o horário de início");
        }

        ReportScopeContext context = resolveContext(scope);
        UUID effectiveStationId = context.effectiveStationId();
        List<ReportRowResponse> rows = switch (type) {
            case WORKED_HOURS -> workedHoursRows(startAt, endAt, context);
            case LIME -> limeRows(startAt, endAt, context);
            case FLOW_MIN_MAX -> flowMinMaxRows(startAt, endAt, context);
            case PH -> phRows(startAt, endAt, effectiveStationId);
            case FLOW -> flowRows(startAt, endAt, effectiveStationId);
            case STOPPAGES -> stoppageRows(startAt, endAt, effectiveStationId);
            case DAILY_MEASUREMENT_AVERAGES -> dailyMeasurementAverageRows(startAt, endAt, context);
        };

        return new ReportResponse(type, startAt, endAt, scope.stationId(), scope.consolidated(), rows);
    }

    private ReportScopeContext resolveContext(StationScope scope) {
        List<Station> stations = stationService.resolveStations(scope.stationId(), scope.consolidated());
        UUID effectiveStationId = scope.consolidated() ? null : scope.stationId();
        return new ReportScopeContext(stations, effectiveStationId);
    }

    public String toCsv(ReportResponse report) {
        List<String> headers = report.rows()
                .stream()
                .flatMap(row -> row.values().keySet().stream())
                .distinct()
                .toList();

        StringBuilder csv = new StringBuilder();
        csv.append(String.join(",", headers)).append("\n");
        for (ReportRowResponse row : report.rows()) {
            csv.append(headers.stream()
                    .map(header -> csvValue(row.values().get(header)))
                    .collect(Collectors.joining(",")));
            csv.append("\n");
        }
        return csv.toString();
    }

    public byte[] toXlsx(ReportResponse report) {
        return ReportWorkbookWriter.write(report);
    }

    // pH unificado: uma tabela de entrada e outra de saída, ambas identificadas pela coluna "waterType".
    private List<ReportRowResponse> phRows(Instant startAt, Instant endAt, UUID stationId) {
        List<ReportRowResponse> rows = new ArrayList<>();
        addPhRows(rows, PhWaterType.RAW, "Entrada", startAt, endAt, stationId);
        addPhRows(rows, PhWaterType.TREATED, "Saída", startAt, endAt, stationId);
        return rows;
    }

    private void addPhRows(
            List<ReportRowResponse> rows,
            PhWaterType type,
            String waterTypeLabel,
            Instant startAt,
            Instant endAt,
            UUID stationId
    ) {
        for (PhMeasurement measurement : phMeasurementRepository.findReportRows(type, startAt, endAt, stationId)) {
            rows.add(row(linkedMap(
                    "stationId", measurement.getStation().getId(),
                    "stationName", measurement.getStation().getName(),
                    "waterType", waterTypeLabel,
                    "ph", measurement.getPh(),
                    "measuredAt", measurement.getMeasuredAt()
            )));
        }
    }

    private List<ReportRowResponse> flowRows(Instant startAt, Instant endAt, UUID stationId) {
        return flowMeasurements(startAt, endAt, stationId)
                .stream()
                .map(this::flowRow)
                .toList();
    }

    // Cal consolidado: entrada e consumo em ordem cronológica por ITE, com saldo corrente (inicial e final).
    private List<ReportRowResponse> limeRows(Instant startAt, Instant endAt, ReportScopeContext context) {
        List<ReportRowResponse> rows = new ArrayList<>();
        for (Station station : context.stations()) {
            BigDecimal balance = limeOpeningBalance(station.getId(), startAt);
            for (LimeRecord record : limeRecordRepository.findReportRows(null, startAt, endAt, station.getId())) {
                BigDecimal initial = balance;
                BigDecimal moved = record.getQuantityBags();
                balance = record.getType() == LimeRecordType.ENTRY ? initial.add(moved) : initial.subtract(moved);
                rows.add(row(linkedMap(
                        "stationId", station.getId(),
                        "stationName", station.getName(),
                        "quantityInitial", initial,
                        "movementType", limeTypeLabel(record.getType()),
                        "quantityMoved", moved,
                        "quantityFinal", balance,
                        "date", localDate(record.getRecordedAt()),
                        "recordedAt", record.getRecordedAt()
                )));
            }
        }
        return rows;
    }

    // Saldo de cal imediatamente antes do início do período (Σ ENTRY − Σ CONSUMPTION).
    private BigDecimal limeOpeningBalance(UUID stationId, Instant startAt) {
        BigDecimal balance = BigDecimal.ZERO;
        for (LimeRecord record : limeRecordRepository.findReportRows(null, Instant.EPOCH, startAt, stationId)) {
            balance = record.getType() == LimeRecordType.ENTRY
                    ? balance.add(record.getQuantityBags())
                    : balance.subtract(record.getQuantityBags());
        }
        return balance;
    }

    private List<ReportRowResponse> stoppageRows(Instant startAt, Instant endAt, UUID stationId) {
        Instant reference = Instant.now();
        List<ReportRowResponse> rows = new ArrayList<>();
        for (Stoppage stoppage : stoppageService.overlapping(stationId, startAt, endAt)) {
            Instant segmentStart = stoppage.getStartAt().isAfter(startAt) ? stoppage.getStartAt() : startAt;
            Instant segmentEnd = effectiveStoppageEnd(stoppage.getEndAt(), endAt, reference);
            if (!segmentEnd.isAfter(segmentStart)) {
                continue;
            }
            Duration duration = Duration.between(segmentStart, segmentEnd);
            ShiftType shift = shiftCalculator.shiftTypeAt(segmentStart);
            rows.add(row(linkedMap(
                    "stationId", stoppage.getStation().getId(),
                    "stationName", stoppage.getStation().getName(),
                    "date", localDate(segmentStart),
                    "shift", shiftLabel(shift),
                    "startAt", segmentStart,
                    "endAt", segmentEnd,
                    "durationHours", hours(duration),
                    "reason", StoppageReasonLabels.describe(stoppage.getReason(), stoppage.getReasonDetail()),
                    "status", statusLabel(stoppage.getStatus())
            )));
        }
        return rows;
    }

    static Instant effectiveStoppageEnd(Instant stoppageEndAt, Instant reportEndAt, Instant reference) {
        Instant effectiveEnd = stoppageEndAt == null ? reference : stoppageEndAt;
        return effectiveEnd.isBefore(reportEndAt) ? effectiveEnd : reportEndAt;
    }

    // Vazão mínima e máxima por dia e por ITE (dias sem medição não geram linha).
    private List<ReportRowResponse> flowMinMaxRows(Instant startAt, Instant endAt, ReportScopeContext context) {
        List<LocalDate> days = reportDays(startAt, endAt);
        List<ReportRowResponse> rows = new ArrayList<>();
        for (Station station : context.stations()) {
            Map<LocalDate, List<FlowMeasurement>> byDay = flowMeasurements(startAt, endAt, station.getId())
                    .stream()
                    .collect(Collectors.groupingBy(
                            measurement -> localDate(measurement.getMeasuredAt()),
                            LinkedHashMap::new,
                            Collectors.toList()
            ));
            for (LocalDate date : days) {
                List<FlowMeasurement> dayMeasurements = byDay.get(date);
                if (dayMeasurements == null || dayMeasurements.isEmpty()) {
                    continue;
                }
                BigDecimal min = dayMeasurements.stream()
                        .map(FlowMeasurement::getFlowLiters)
                        .min(BigDecimal::compareTo)
                        .orElse(null);
                BigDecimal max = dayMeasurements.stream()
                        .map(FlowMeasurement::getFlowLiters)
                        .max(BigDecimal::compareTo)
                        .orElse(null);
                rows.add(row(linkedMap(
                        "stationId", station.getId(),
                        "stationName", station.getName(),
                        "date", date,
                        "minFlowLiters", min,
                        "maxFlowLiters", max
                )));
            }
        }
        return rows;
    }

    // Horas trabalhadas e horas paradas por dia e por ITE.
    private List<ReportRowResponse> workedHoursRows(Instant startAt, Instant endAt, ReportScopeContext context) {
        List<LocalDate> days = reportDays(startAt, endAt);
        List<ReportRowResponse> rows = new ArrayList<>();
        for (Station station : context.stations()) {
            Set<LocalDate> activityDays = workedHoursActivityDays(startAt, endAt, station.getId());
            for (LocalDate date : days) {
                if (!activityDays.contains(date)) {
                    continue;
                }
                DayHours dayHours = workedAndDowntimeForDay(station.getId(), date, startAt, endAt);
                rows.add(row(linkedMap(
                        "stationId", station.getId(),
                        "stationName", station.getName(),
                        "date", date,
                        "workedHours", dayHours.worked(),
                        "downtimeHours", dayHours.downtime()
                )));
            }
        }
        return rows;
    }

    private Set<LocalDate> workedHoursActivityDays(Instant startAt, Instant endAt, UUID stationId) {
        Set<LocalDate> days = new HashSet<>();
        List<FlowMeasurement> flowMeasurements = flowMeasurements(startAt, endAt, stationId);
        if (flowMeasurements != null) {
            flowMeasurements.stream()
                    .map(measurement -> localDate(measurement.getMeasuredAt()))
                    .forEach(days::add);
        }

        addPhActivityDays(days, PhWaterType.RAW, startAt, endAt, stationId);
        addPhActivityDays(days, PhWaterType.TREATED, startAt, endAt, stationId);

        List<LimeRecord> limeRecords = limeRecordRepository.findReportRows(null, startAt, endAt, stationId);
        if (limeRecords != null) {
            limeRecords.stream()
                    .map(record -> localDate(record.getRecordedAt()))
                    .forEach(days::add);
        }

        Instant reference = Instant.now();
        List<Stoppage> stoppages = stoppageService.overlapping(stationId, startAt, endAt);
        if (stoppages == null) {
            return days;
        }
        for (Stoppage stoppage : stoppages) {
            Instant activityStart = stoppage.getStartAt().isAfter(startAt) ? stoppage.getStartAt() : startAt;
            Instant activityEnd = effectiveStoppageEnd(stoppage.getEndAt(), endAt, reference);
            addDaysInWindow(days, activityStart, activityEnd);
        }
        return days;
    }

    private void addPhActivityDays(
            Set<LocalDate> days,
            PhWaterType type,
            Instant startAt,
            Instant endAt,
            UUID stationId
    ) {
        List<PhMeasurement> measurements = phMeasurementRepository.findReportRows(type, startAt, endAt, stationId);
        if (measurements == null) {
            return;
        }
        measurements.stream()
                .map(measurement -> localDate(measurement.getMeasuredAt()))
                .forEach(days::add);
    }

    private List<ReportRowResponse> dailyMeasurementAverageRows(
            Instant startAt,
            Instant endAt,
            ReportScopeContext context
    ) {
        Map<StationDayKey, DailyMeasurementAverageAccumulator> averages = new LinkedHashMap<>();

        for (PhMeasurement measurement : phMeasurementRepository.findReportRows(PhWaterType.RAW, startAt, endAt, context.effectiveStationId())) {
            accumulatorFor(averages, measurement.getStation(), measurement.getMeasuredAt()).addRawPh(measurement.getPh());
        }

        for (PhMeasurement measurement : phMeasurementRepository.findReportRows(PhWaterType.TREATED, startAt, endAt, context.effectiveStationId())) {
            accumulatorFor(averages, measurement.getStation(), measurement.getMeasuredAt()).addTreatedPh(measurement.getPh());
        }

        for (FlowMeasurement measurement : flowMeasurements(startAt, endAt, context.effectiveStationId())) {
            accumulatorFor(averages, measurement.getStation(), measurement.getMeasuredAt()).addFlow(measurement.getFlowLiters());
        }

        return averages.entrySet()
                .stream()
                .map(entry -> dailyMeasurementAverageRow(entry.getKey(), entry.getValue(), startAt, endAt))
                .toList();
    }

    private List<FlowMeasurement> flowMeasurements(Instant startAt, Instant endAt, UUID stationId) {
        return flowMeasurementRepository.findReportRows(startAt, endAt, stationId);
    }

    private DailyMeasurementAverageAccumulator accumulatorFor(
            Map<StationDayKey, DailyMeasurementAverageAccumulator> averages,
            Station station,
            Instant measuredAt
    ) {
        StationDayKey key = new StationDayKey(station.getId(), station.getName(), localDate(measuredAt));
        return averages.computeIfAbsent(key, ignored -> new DailyMeasurementAverageAccumulator());
    }

    private ReportRowResponse dailyMeasurementAverageRow(
            StationDayKey key,
            DailyMeasurementAverageAccumulator accumulator,
            Instant reportStart,
            Instant reportEnd
    ) {
        BigDecimal workedHours = workedHoursForDay(key.stationId(), key.date(), reportStart, reportEnd);
        BigDecimal flowAverage = accumulator.flowAverageOrZero();
        BigDecimal flowTotal = flowAverage.multiply(workedHours).setScale(3, RoundingMode.HALF_UP);
        BigDecimal flowPerHour = workedHours.compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ZERO.setScale(3, RoundingMode.HALF_UP)
                : flowTotal.divide(workedHours, 3, RoundingMode.HALF_UP);

        return row(linkedMap(
                "stationId", key.stationId(),
                "stationName", key.stationName(),
                "date", key.date(),
                "rawPhAverage", accumulator.rawPhAverage(),
                "treatedPhAverage", accumulator.treatedPhAverage(),
                "workedHours", workedHours,
                "flowTotal", flowTotal,
                "flowPerHour", flowPerHour,
                "flowAverage", flowAverage
        ));
    }

    private BigDecimal workedHoursForDay(UUID stationId, LocalDate date, Instant reportStart, Instant reportEnd) {
        return workedAndDowntimeForDay(stationId, date, reportStart, reportEnd).worked();
    }

    // Horas trabalhadas e paradas de um dia, recortadas pela janela do relatório.
    private DayHours workedAndDowntimeForDay(UUID stationId, LocalDate date, Instant reportStart, Instant reportEnd) {
        Instant dayStart = date.atStartOfDay(BUSINESS_ZONE).toInstant();
        Instant dayEnd = date.plusDays(1).atStartOfDay(BUSINESS_ZONE).toInstant();
        Instant segmentStart = dayStart.isAfter(reportStart) ? dayStart : reportStart;
        Instant segmentEnd = dayEnd.isBefore(reportEnd) ? dayEnd : reportEnd;
        if (!segmentEnd.isAfter(segmentStart)) {
            return new DayHours(zeroHours(), zeroHours());
        }
        Duration elapsed = Duration.between(segmentStart, segmentEnd);
        Duration downtime = stoppageService.downtimeBetween(stationId, segmentStart, segmentEnd);
        if (downtime == null) {
            downtime = Duration.ZERO;
        }
        Duration worked = elapsed.minus(downtime);
        if (worked.isNegative()) {
            worked = Duration.ZERO;
        }
        return new DayHours(hours(worked), hours(downtime));
    }

    // Dias do período com janela não vazia (recortados por início/fim do relatório).
    private List<LocalDate> reportDays(Instant startAt, Instant endAt) {
        List<LocalDate> days = new ArrayList<>();
        LocalDate last = localDate(endAt);
        for (LocalDate date = localDate(startAt); !date.isAfter(last); date = date.plusDays(1)) {
            Instant dayStart = date.atStartOfDay(BUSINESS_ZONE).toInstant();
            Instant dayEnd = date.plusDays(1).atStartOfDay(BUSINESS_ZONE).toInstant();
            Instant segmentStart = dayStart.isAfter(startAt) ? dayStart : startAt;
            Instant segmentEnd = dayEnd.isBefore(endAt) ? dayEnd : endAt;
            if (segmentEnd.isAfter(segmentStart)) {
                days.add(date);
            }
        }
        return days;
    }

    private void addDaysInWindow(Set<LocalDate> days, Instant startAt, Instant endAt) {
        if (!endAt.isAfter(startAt)) {
            return;
        }
        days.addAll(reportDays(startAt, endAt));
    }

    private LocalDate localDate(Instant instant) {
        return instant.atZone(BUSINESS_ZONE).toLocalDate();
    }

    private ReportRowResponse flowRow(FlowMeasurement measurement) {
        return row(linkedMap(
                "id", measurement.getId(),
                "stationId", measurement.getStation().getId(),
                "stationName", measurement.getStation().getName(),
                "flowLiters", measurement.getFlowLiters(),
                "measuredAt", measurement.getMeasuredAt()
        ));
    }

    private static String limeTypeLabel(LimeRecordType type) {
        return switch (type) {
            case ENTRY -> "Entrada";
            case CONSUMPTION -> "Consumo";
        };
    }

    private static String shiftLabel(ShiftType shift) {
        return shift == ShiftType.DAY ? "Dia" : "Noite";
    }

    private static String statusLabel(StoppageStatus status) {
        return status == StoppageStatus.ACTIVE ? "Ativa" : "Finalizada";
    }

    private ReportRowResponse row(Map<String, Object> values) {
        return new ReportRowResponse(values);
    }

    private Map<String, Object> linkedMap(Object... entries) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (int index = 0; index < entries.length; index += 2) {
            values.put((String) entries[index], entries[index + 1]);
        }
        return values;
    }

    private BigDecimal hours(Duration duration) {
        return BigDecimal.valueOf(duration.toSeconds()).divide(BigDecimal.valueOf(3600), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal zeroHours() {
        return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    private String csvValue(Object value) {
        if (value == null) {
            return "";
        }
        String text = value.toString();
        if (text.contains(",") || text.contains("\"") || text.contains("\n")) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }

    private record StationDayKey(UUID stationId, String stationName, LocalDate date) {
    }

    private record DayHours(BigDecimal worked, BigDecimal downtime) {
    }

    private static class DailyMeasurementAverageAccumulator {

        private BigDecimal rawPhTotal = BigDecimal.ZERO;
        private int rawPhCount;
        private BigDecimal treatedPhTotal = BigDecimal.ZERO;
        private int treatedPhCount;
        private BigDecimal flowTotal = BigDecimal.ZERO;
        private int flowCount;

        void addRawPh(BigDecimal value) {
            rawPhTotal = rawPhTotal.add(value);
            rawPhCount += 1;
        }

        void addTreatedPh(BigDecimal value) {
            treatedPhTotal = treatedPhTotal.add(value);
            treatedPhCount += 1;
        }

        void addFlow(BigDecimal value) {
            flowTotal = flowTotal.add(value);
            flowCount += 1;
        }

        BigDecimal rawPhAverage() {
            return average(rawPhTotal, rawPhCount);
        }

        BigDecimal treatedPhAverage() {
            return average(treatedPhTotal, treatedPhCount);
        }

        BigDecimal flowAverage() {
            return average(flowTotal, flowCount);
        }

        BigDecimal flowAverageOrZero() {
            BigDecimal average = flowAverage();
            return average == null ? BigDecimal.ZERO.setScale(3, RoundingMode.HALF_UP) : average;
        }

        private BigDecimal average(BigDecimal total, int count) {
            if (count == 0) {
                return null;
            }
            return total.divide(BigDecimal.valueOf(count), AVERAGE_CONTEXT).setScale(3, RoundingMode.HALF_UP);
        }
    }

    private record ReportScopeContext(
            List<Station> stations,
            UUID effectiveStationId
    ) {
    }
}
