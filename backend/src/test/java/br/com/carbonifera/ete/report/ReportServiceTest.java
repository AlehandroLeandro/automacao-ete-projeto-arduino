package br.com.carbonifera.ete.report;

import br.com.carbonifera.ete.lime.LimeRecord;
import br.com.carbonifera.ete.lime.LimeRecordRepository;
import br.com.carbonifera.ete.lime.LimeRecordType;
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
import br.com.carbonifera.ete.stoppage.Stoppage;
import br.com.carbonifera.ete.stoppage.StoppageReason;
import br.com.carbonifera.ete.stoppage.StoppageService;
import br.com.carbonifera.ete.user.AppUser;
import br.com.carbonifera.ete.user.UserRole;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReportServiceTest {

    @Test
    void effectiveStoppageEndUsesCurrentReferenceForActiveStoppages() {
        Instant reportEnd = Instant.parse("2026-06-01T23:59:00Z");
        Instant reference = Instant.parse("2026-06-01T13:00:00Z");

        Instant effectiveEnd = ReportService.effectiveStoppageEnd(null, reportEnd, reference);

        assertThat(effectiveEnd).isEqualTo(reference);
    }

    @Test
    void effectiveStoppageEndNeverPassesReportEnd() {
        Instant reportEnd = Instant.parse("2026-06-01T23:59:00Z");
        Instant reference = Instant.parse("2026-06-02T01:00:00Z");

        Instant effectiveEnd = ReportService.effectiveStoppageEnd(null, reportEnd, reference);

        assertThat(effectiveEnd).isEqualTo(reportEnd);
    }

    @Test
    void effectiveStoppageEndUsesFinishedStoppageEndWhenEarlierThanReportEnd() {
        Instant stoppageEnd = Instant.parse("2026-06-01T15:00:00Z");
        Instant reportEnd = Instant.parse("2026-06-01T23:59:00Z");
        Instant reference = Instant.parse("2026-06-01T16:00:00Z");

        Instant effectiveEnd = ReportService.effectiveStoppageEnd(stoppageEnd, reportEnd, reference);

        assertThat(effectiveEnd).isEqualTo(stoppageEnd);
    }

    @Test
    void flowMinMaxReportKeepsResolvedOperatorStationScope() {
        Instant startAt = Instant.parse("2026-07-01T00:00:00Z");
        Instant endAt = Instant.parse("2026-07-01T02:00:00Z");
        Station assignedStation = station(UUID.randomUUID(), "ITE A");
        Station otherStation = station(UUID.randomUUID(), "ITE B");
        AppUser operator = user(UserRole.OPERATOR, assignedStation);
        FlowMeasurementRepository flowMeasurementRepository = mock(FlowMeasurementRepository.class);
        ReportService service = reportService(
                List.of(assignedStation, otherStation),
                operator,
                flowMeasurementRepository
        );
        when(flowMeasurementRepository.findReportRows(startAt, endAt, assignedStation.getId()))
                .thenReturn(List.of(new FlowMeasurement(
                        assignedStation,
                        new BigDecimal("120.00"),
                        startAt,
                        operator
                )));

        ReportResponse report = service.generate(
                ReportType.FLOW_MIN_MAX,
                startAt,
                endAt,
                null,
                true,
                UserPrincipal.from(operator)
        );

        assertThat(report.consolidated()).isFalse();
        assertThat(report.stationId()).isEqualTo(assignedStation.getId());
        assertThat(report.rows()).isNotEmpty();
        assertThat(report.rows().getFirst().values().get("stationId")).isEqualTo(assignedStation.getId());
        verify(flowMeasurementRepository).findReportRows(startAt, endAt, assignedStation.getId());
        verify(flowMeasurementRepository, never()).findReportRows(eq(startAt), eq(endAt), isNull());
    }

    @Test
    void workedHoursReportKeepsResolvedOperatorStationScope() {
        Instant startAt = Instant.parse("2026-07-01T00:00:00Z");
        Instant endAt = Instant.parse("2026-07-01T02:00:00Z");
        Station assignedStation = station(UUID.randomUUID(), "ITE A");
        Station otherStation = station(UUID.randomUUID(), "ITE B");
        AppUser operator = user(UserRole.OPERATOR, assignedStation);
        FlowMeasurementRepository flowMeasurementRepository = mock(FlowMeasurementRepository.class);
        StoppageService stoppageService = mock(StoppageService.class);
        ReportService service = reportService(
                List.of(assignedStation, otherStation),
                operator,
                flowMeasurementRepository,
                stoppageService
        );
        when(flowMeasurementRepository.findReportRows(startAt, endAt, assignedStation.getId()))
                .thenReturn(List.of(new FlowMeasurement(
                        assignedStation,
                        new BigDecimal("120.00"),
                        startAt,
                        operator
                )));
        when(stoppageService.downtimeBetween(eq(assignedStation.getId()), eq(startAt), eq(endAt)))
                .thenReturn(Duration.ZERO);

        ReportResponse report = service.generate(
                ReportType.WORKED_HOURS,
                startAt,
                endAt,
                null,
                true,
                UserPrincipal.from(operator)
        );

        assertThat(report.consolidated()).isFalse();
        assertThat(report.stationId()).isEqualTo(assignedStation.getId());
        assertThat(report.rows()).isNotEmpty();
        assertThat(report.rows().getFirst().values().get("stationId")).isEqualTo(assignedStation.getId());
        verify(stoppageService, never()).downtimeBetween(isNull(), eq(startAt), eq(endAt));
    }

    @Test
    void dailyMeasurementAverageReportGroupsMeasurementsByStationAndDay() {
        Instant startAt = Instant.parse("2026-07-01T03:00:00Z");
        Instant endAt = Instant.parse("2026-07-03T03:00:00Z");
        Station station = station(UUID.randomUUID(), "ITE A");
        AppUser operator = user(UserRole.OPERATOR, station);
        PhMeasurementRepository phMeasurementRepository = mock(PhMeasurementRepository.class);
        FlowMeasurementRepository flowMeasurementRepository = mock(FlowMeasurementRepository.class);
        ReportService service = reportService(
                List.of(station),
                operator,
                phMeasurementRepository,
                flowMeasurementRepository,
                mock(StoppageService.class)
        );

        when(phMeasurementRepository.findReportRows(PhWaterType.RAW, startAt, endAt, station.getId()))
                .thenReturn(List.of(
                        new PhMeasurement(station, PhWaterType.RAW, new BigDecimal("6.00"), Instant.parse("2026-07-01T10:00:00Z"), operator),
                        new PhMeasurement(station, PhWaterType.RAW, new BigDecimal("7.00"), Instant.parse("2026-07-01T12:00:00Z"), operator)
                ));
        when(phMeasurementRepository.findReportRows(PhWaterType.TREATED, startAt, endAt, station.getId()))
                .thenReturn(List.of(
                        new PhMeasurement(station, PhWaterType.TREATED, new BigDecimal("7.00"), Instant.parse("2026-07-01T10:00:00Z"), operator),
                        new PhMeasurement(station, PhWaterType.TREATED, new BigDecimal("7.40"), Instant.parse("2026-07-01T12:00:00Z"), operator)
                ));
        when(flowMeasurementRepository.findReportRows(startAt, endAt, station.getId()))
                .thenReturn(List.of(
                        new FlowMeasurement(station, new BigDecimal("100.00"), Instant.parse("2026-07-01T10:00:00Z"), operator),
                        new FlowMeasurement(station, new BigDecimal("120.00"), Instant.parse("2026-07-01T12:00:00Z"), operator)
                ));

        ReportResponse report = service.generate(
                ReportType.DAILY_MEASUREMENT_AVERAGES,
                startAt,
                endAt,
                station.getId(),
                false,
                UserPrincipal.from(operator)
        );

        assertThat(report.rows()).hasSize(1);
        assertThat(report.rows().getFirst().values())
                .containsEntry("rawPhAverage", new BigDecimal("6.500"))
                .containsEntry("treatedPhAverage", new BigDecimal("7.200"))
                .containsEntry("flowAverage", new BigDecimal("110.000"));
    }

    @Test
    void flowMinMaxReportOmitsDaysWithoutFlowMeasurements() {
        Instant startAt = Instant.parse("2026-07-01T03:00:00Z");
        Instant endAt = Instant.parse("2026-07-04T03:00:00Z");
        Station station = station(UUID.randomUUID(), "ITE A");
        AppUser operator = user(UserRole.OPERATOR, station);
        FlowMeasurementRepository flowMeasurementRepository = mock(FlowMeasurementRepository.class);
        ReportService service = reportService(
                List.of(station),
                operator,
                flowMeasurementRepository
        );
        when(flowMeasurementRepository.findReportRows(startAt, endAt, station.getId()))
                .thenReturn(List.of(
                        new FlowMeasurement(station, new BigDecimal("100.00"), Instant.parse("2026-07-01T10:00:00Z"), operator),
                        new FlowMeasurement(station, new BigDecimal("120.00"), Instant.parse("2026-07-03T10:00:00Z"), operator)
                ));

        ReportResponse report = service.generate(
                ReportType.FLOW_MIN_MAX,
                startAt,
                endAt,
                station.getId(),
                false,
                UserPrincipal.from(operator)
        );

        assertThat(report.rows())
                .extracting(row -> row.values().get("date"))
                .containsExactly(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 3));
    }

    @Test
    void workedHoursReportOmitsDaysWithoutActivityRecords() {
        Instant startAt = Instant.parse("2026-07-01T03:00:00Z");
        Instant endAt = Instant.parse("2026-07-04T03:00:00Z");
        Station station = station(UUID.randomUUID(), "ITE A");
        AppUser operator = user(UserRole.OPERATOR, station);
        FlowMeasurementRepository flowMeasurementRepository = mock(FlowMeasurementRepository.class);
        StoppageService stoppageService = mock(StoppageService.class);
        ReportService service = reportService(
                List.of(station),
                operator,
                flowMeasurementRepository,
                stoppageService
        );
        when(flowMeasurementRepository.findReportRows(startAt, endAt, station.getId()))
                .thenReturn(List.of(
                        new FlowMeasurement(station, new BigDecimal("100.00"), Instant.parse("2026-07-01T10:00:00Z"), operator),
                        new FlowMeasurement(station, new BigDecimal("120.00"), Instant.parse("2026-07-03T10:00:00Z"), operator)
                ));
        when(stoppageService.downtimeBetween(eq(station.getId()), eq(startAt), eq(Instant.parse("2026-07-02T03:00:00Z"))))
                .thenReturn(Duration.ZERO);
        when(stoppageService.downtimeBetween(eq(station.getId()), eq(Instant.parse("2026-07-03T03:00:00Z")), eq(endAt)))
                .thenReturn(Duration.ZERO);

        ReportResponse report = service.generate(
                ReportType.WORKED_HOURS,
                startAt,
                endAt,
                station.getId(),
                false,
                UserPrincipal.from(operator)
        );

        assertThat(report.rows())
                .extracting(row -> row.values().get("date"))
                .containsExactly(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 3));
    }

    @Test
    void workedHoursReportIncludesDaysWithAnyOperationalRecord() {
        Instant startAt = Instant.parse("2026-07-01T03:00:00Z");
        Instant endAt = Instant.parse("2026-07-05T03:00:00Z");
        Station station = station(UUID.randomUUID(), "ITE A");
        AppUser operator = user(UserRole.OPERATOR, station);
        PhMeasurementRepository phMeasurementRepository = mock(PhMeasurementRepository.class);
        FlowMeasurementRepository flowMeasurementRepository = mock(FlowMeasurementRepository.class);
        LimeRecordRepository limeRecordRepository = mock(LimeRecordRepository.class);
        StoppageService stoppageService = mock(StoppageService.class);
        ReportService service = reportService(
                List.of(station),
                operator,
                phMeasurementRepository,
                flowMeasurementRepository,
                limeRecordRepository,
                stoppageService
        );
        when(phMeasurementRepository.findReportRows(PhWaterType.RAW, startAt, endAt, station.getId()))
                .thenReturn(List.of(new PhMeasurement(
                        station,
                        PhWaterType.RAW,
                        new BigDecimal("6.80"),
                        Instant.parse("2026-07-01T10:00:00Z"),
                        operator
                )));
        when(phMeasurementRepository.findReportRows(PhWaterType.TREATED, startAt, endAt, station.getId()))
                .thenReturn(List.of(new PhMeasurement(
                        station,
                        PhWaterType.TREATED,
                        new BigDecimal("7.10"),
                        Instant.parse("2026-07-02T10:00:00Z"),
                        operator
                )));
        when(limeRecordRepository.findReportRows(null, startAt, endAt, station.getId()))
                .thenReturn(List.of(new LimeRecord(
                        station,
                        LimeRecordType.CONSUMPTION,
                        new BigDecimal("1.00"),
                        Instant.parse("2026-07-03T10:00:00Z"),
                        null,
                        operator
                )));
        when(flowMeasurementRepository.findReportRows(startAt, endAt, station.getId()))
                .thenReturn(List.of(new FlowMeasurement(
                        station,
                        new BigDecimal("100.00"),
                        Instant.parse("2026-07-04T10:00:00Z"),
                        operator
                )));

        ReportResponse report = service.generate(
                ReportType.WORKED_HOURS,
                startAt,
                endAt,
                station.getId(),
                false,
                UserPrincipal.from(operator)
        );

        assertThat(report.rows())
                .extracting(row -> row.values().get("date"))
                .containsExactly(
                        LocalDate.of(2026, 7, 1),
                        LocalDate.of(2026, 7, 2),
                        LocalDate.of(2026, 7, 3),
                        LocalDate.of(2026, 7, 4)
                );
    }

    @Test
    void stoppageReportIncludesClippedStartAndEndTimes() {
        Instant startAt = Instant.parse("2026-07-01T08:00:00Z");
        Instant endAt = Instant.parse("2026-07-01T12:00:00Z");
        Station station = station(UUID.randomUUID(), "ITE A");
        AppUser operator = user(UserRole.OPERATOR, station);
        StoppageService stoppageService = mock(StoppageService.class);
        ShiftCalculator shiftCalculator = mock(ShiftCalculator.class);
        FakeStationService stationService = new FakeStationService(List.of(station));
        ReportService service = new ReportService(
                mock(PhMeasurementRepository.class),
                mock(FlowMeasurementRepository.class),
                mock(LimeRecordRepository.class),
                stoppageService,
                stationService,
                new StationScopeService(stationService, new FakeCurrentUserService(operator)),
                shiftCalculator
        );
        Stoppage stoppage = new Stoppage(
                station,
                StoppageReason.FALTA_DE_AGUA,
                Instant.parse("2026-07-01T02:00:00Z"),
                operator
        );
        stoppage.finish(Instant.parse("2026-07-01T13:00:00Z"));
        when(stoppageService.overlapping(station.getId(), startAt, endAt)).thenReturn(List.of(stoppage));
        when(shiftCalculator.shiftTypeAt(startAt)).thenReturn(ShiftType.DAY);

        ReportResponse report = service.generate(
                ReportType.STOPPAGES,
                startAt,
                endAt,
                station.getId(),
                false,
                UserPrincipal.from(operator)
        );

        assertThat(report.rows().getFirst().values())
                .containsEntry("date", LocalDate.of(2026, 7, 1))
                .containsEntry("shift", "Dia")
                .containsEntry("startAt", startAt)
                .containsEntry("endAt", endAt)
                .containsEntry("durationHours", new BigDecimal("4.00"));
    }

    @Test
    void dailyMeasurementAverageComputesFlowMetricsFromWorkedHours() {
        Instant startAt = Instant.parse("2026-07-07T03:00:00Z");
        Instant endAt = Instant.parse("2026-07-07T13:00:00Z");
        Station station = station(UUID.randomUUID(), "ITE A");
        AppUser operator = user(UserRole.OPERATOR, station);
        PhMeasurementRepository phMeasurementRepository = mock(PhMeasurementRepository.class);
        FlowMeasurementRepository flowMeasurementRepository = mock(FlowMeasurementRepository.class);
        StoppageService stoppageService = mock(StoppageService.class);
        ReportService service = reportService(
                List.of(station),
                operator,
                phMeasurementRepository,
                flowMeasurementRepository,
                stoppageService
        );
        when(flowMeasurementRepository.findReportRows(startAt, endAt, station.getId()))
                .thenReturn(List.of(new FlowMeasurement(
                        station,
                        new BigDecimal("100.00"),
                        Instant.parse("2026-07-07T05:00:00Z"),
                        operator
                )));
        when(stoppageService.downtimeBetween(station.getId(), startAt, endAt))
                .thenReturn(Duration.ofMinutes(30));

        ReportResponse report = service.generate(
                ReportType.DAILY_MEASUREMENT_AVERAGES,
                startAt,
                endAt,
                station.getId(),
                false,
                UserPrincipal.from(operator)
        );

        Map<String, Object> values = report.rows().getFirst().values();
        assertThat((BigDecimal) values.get("workedHours")).isEqualByComparingTo("9.50");
        assertThat((BigDecimal) values.get("flowAverage")).isEqualByComparingTo("100.000");
        assertThat((BigDecimal) values.get("flowTotal")).isEqualByComparingTo("950.000");
        assertThat((BigDecimal) values.get("flowPerHour")).isEqualByComparingTo("100.000");
    }

    @Test
    void dailyMeasurementAverageReturnsZeroFlowPerHourWhenWorkedHoursIsZero() {
        Instant startAt = Instant.parse("2026-07-07T03:00:00Z");
        Instant endAt = Instant.parse("2026-07-07T13:00:00Z");
        Station station = station(UUID.randomUUID(), "ITE A");
        AppUser operator = user(UserRole.OPERATOR, station);
        PhMeasurementRepository phMeasurementRepository = mock(PhMeasurementRepository.class);
        FlowMeasurementRepository flowMeasurementRepository = mock(FlowMeasurementRepository.class);
        StoppageService stoppageService = mock(StoppageService.class);
        ReportService service = reportService(
                List.of(station),
                operator,
                phMeasurementRepository,
                flowMeasurementRepository,
                stoppageService
        );
        when(flowMeasurementRepository.findReportRows(startAt, endAt, station.getId()))
                .thenReturn(List.of(new FlowMeasurement(
                        station,
                        new BigDecimal("100.00"),
                        Instant.parse("2026-07-07T05:00:00Z"),
                        operator
                )));
        when(stoppageService.downtimeBetween(station.getId(), startAt, endAt))
                .thenReturn(Duration.ofHours(10));

        ReportResponse report = service.generate(
                ReportType.DAILY_MEASUREMENT_AVERAGES,
                startAt,
                endAt,
                station.getId(),
                false,
                UserPrincipal.from(operator)
        );

        Map<String, Object> values = report.rows().getFirst().values();
        assertThat((BigDecimal) values.get("workedHours")).isEqualByComparingTo("0.00");
        assertThat((BigDecimal) values.get("flowTotal")).isEqualByComparingTo("0.000");
        assertThat((BigDecimal) values.get("flowPerHour")).isEqualByComparingTo("0.000");
    }

    @ParameterizedTest
    @EnumSource(ReportType.class)
    void xlsxExportSupportsEveryReportType(ReportType type) throws Exception {
        ReportService service = reportService();
        ReportResponse report = report(type, sampleRows(type));

        byte[] content = service.toXlsx(report);

        try (XSSFWorkbook workbook = openWorkbook(content)) {
            assertThat(workbook.getNumberOfSheets()).isGreaterThan(0);
        }
    }

    @Test
    void xlsxStartsAtColumnHeaderRowWithoutMetadataBlock() throws Exception {
        ReportService service = reportService();
        ReportResponse report = report(ReportType.FLOW, sampleRows(ReportType.FLOW));

        byte[] content = service.toXlsx(report);

        try (XSSFWorkbook workbook = openWorkbook(content)) {
            Sheet sheet = workbook.getSheetAt(0);
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Coletado em");
            assertThat(sheet.getRow(0).getCell(1).getStringCellValue()).isEqualTo("Vazão");
            assertThat(sheet.getRow(1).getCell(1).getCellType()).isEqualTo(CellType.NUMERIC);
        }
    }

    @Test
    void xlsxFlowReportAppendsPeriodAverageAtEndOfFlowColumn() throws Exception {
        ReportService service = reportService();
        ReportResponse report = report(
                ReportType.FLOW,
                List.of(
                        row(Map.of("stationName", "ITE A", "flowLiters", new BigDecimal("100.00"), "measuredAt", Instant.parse("2026-07-01T10:00:00Z"))),
                        row(Map.of("stationName", "ITE A", "flowLiters", new BigDecimal("120.00"), "measuredAt", Instant.parse("2026-07-01T11:00:00Z")))
                )
        );

        byte[] content = service.toXlsx(report);

        try (XSSFWorkbook workbook = openWorkbook(content)) {
            Sheet sheet = workbook.getSheetAt(0);
            assertThat(sheet.getRow(3).getCell(0).getStringCellValue()).isEqualTo("MÉDIA");
            assertThat(sheet.getRow(3).getCell(1).getNumericCellValue()).isEqualTo(110.0);
        }
    }

    @Test
    void xlsxWorkedHoursReportAppendsTotalRow() throws Exception {
        ReportService service = reportService();
        ReportResponse report = report(
                ReportType.WORKED_HOURS,
                List.of(
                        row(Map.of("stationName", "ITE A", "date", LocalDate.of(2026, 7, 1), "workedHours", new BigDecimal("9.50"), "downtimeHours", new BigDecimal("0.50"))),
                        row(Map.of("stationName", "ITE A", "date", LocalDate.of(2026, 7, 2), "workedHours", new BigDecimal("8.00"), "downtimeHours", new BigDecimal("1.00")))
                )
        );

        byte[] content = service.toXlsx(report);

        try (XSSFWorkbook workbook = openWorkbook(content)) {
            Sheet sheet = workbook.getSheetAt(0);
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Data");
            assertThat(sheet.getRow(0).getCell(1).getStringCellValue()).isEqualTo("Horas trabalhadas");
            assertThat(sheet.getRow(0).getCell(2).getStringCellValue()).isEqualTo("Horas paradas");
            assertThat(sheet.getRow(3).getCell(0).getStringCellValue()).isEqualTo("TOTAL");
            assertThat(sheet.getRow(3).getCell(1).getNumericCellValue()).isEqualTo(17.5 / 24.0);
            assertThat(sheet.getRow(3).getCell(2).getNumericCellValue()).isEqualTo(1.5 / 24.0);
        }
    }

    @Test
    void xlsxPhReportWritesEntryAndOutputTablesSideBySide() throws Exception {
        ReportService service = reportService();
        ReportResponse report = report(
                ReportType.PH,
                List.of(
                        row(Map.of("stationName", "ITE A", "waterType", "Entrada", "ph", new BigDecimal("6.75"), "measuredAt", Instant.parse("2026-07-01T10:00:00Z"))),
                        row(Map.of("stationName", "ITE A", "waterType", "Saída", "ph", new BigDecimal("7.20"), "measuredAt", Instant.parse("2026-07-01T10:00:00Z")))
                )
        );

        byte[] content = service.toXlsx(report);

        try (XSSFWorkbook workbook = openWorkbook(content)) {
            Sheet sheet = workbook.getSheetAt(0);
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("pH de entrada");
            assertThat(sheet.getRow(0).getCell(3).getStringCellValue()).isEqualTo("pH de saída");
            assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).isEqualTo("pH");
            assertThat(sheet.getRow(1).getCell(3).getStringCellValue()).isEqualTo("pH");
            assertThat(sheet.getRow(2).getCell(0).getNumericCellValue()).isEqualTo(6.75);
            assertThat(sheet.getRow(2).getCell(3).getNumericCellValue()).isEqualTo(7.20);
        }
    }

    @Test
    void xlsxLimeReportWritesRunningBalanceColumns() throws Exception {
        ReportService service = reportService();
        ReportResponse report = report(
                ReportType.LIME,
                List.of(row(Map.of(
                        "stationName", "ITE A",
                        "quantityInitial", new BigDecimal("10.00"),
                        "movementType", "Consumo",
                        "quantityMoved", new BigDecimal("4.00"),
                        "quantityFinal", new BigDecimal("6.00"),
                        "date", LocalDate.of(2026, 7, 1),
                        "recordedAt", Instant.parse("2026-07-01T10:00:00Z")
                )))
        );

        byte[] content = service.toXlsx(report);

        try (XSSFWorkbook workbook = openWorkbook(content)) {
            Sheet sheet = workbook.getSheetAt(0);
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Data");
            assertThat(sheet.getRow(0).getCell(1).getStringCellValue()).isEqualTo("Horário");
            assertThat(sheet.getRow(0).getCell(2).getStringCellValue()).isEqualTo("Quantidade inicial");
            assertThat(sheet.getRow(0).getCell(3).getStringCellValue()).isEqualTo("Tipo de movimento");
            assertThat(sheet.getRow(0).getCell(4).getStringCellValue()).isEqualTo("Quantidade movimentada");
            assertThat(sheet.getRow(0).getCell(5).getStringCellValue()).isEqualTo("Quantidade final");
            assertThat(sheet.getRow(1).getCell(2).getNumericCellValue()).isEqualTo(10.0);
            assertThat(sheet.getRow(1).getCell(3).getStringCellValue()).isEqualTo("Consumo");
            assertThat(sheet.getRow(1).getCell(5).getNumericCellValue()).isEqualTo(6.0);
        }
    }

    @Test
    void xlsxStoppagesReportWritesStartAndEndTimeBeforeDuration() throws Exception {
        ReportService service = reportService();
        ReportResponse report = report(ReportType.STOPPAGES, sampleRows(ReportType.STOPPAGES));

        byte[] content = service.toXlsx(report);

        try (XSSFWorkbook workbook = openWorkbook(content)) {
            Sheet sheet = workbook.getSheetAt(0);
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Data");
            assertThat(sheet.getRow(0).getCell(1).getStringCellValue()).isEqualTo("Turno");
            assertThat(sheet.getRow(0).getCell(2).getStringCellValue()).isEqualTo("Horário de início");
            assertThat(sheet.getRow(0).getCell(3).getStringCellValue()).isEqualTo("Horário de fim");
            assertThat(sheet.getRow(0).getCell(4).getStringCellValue()).isEqualTo("Duração");
            assertThat(sheet.getRow(0).getCell(5).getStringCellValue()).isEqualTo("Motivo");
            assertThat(sheet.getRow(0).getCell(6).getStringCellValue()).isEqualTo("Status");
            assertThat(sheet.getRow(1).getCell(1).getStringCellValue()).isEqualTo("Dia");
            assertThat(sheet.getRow(1).getCell(2).getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(sheet.getRow(1).getCell(3).getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(sheet.getRow(1).getCell(4).getCellType()).isEqualTo(CellType.NUMERIC);
        }
    }

    @Test
    void xlsxDailyAveragesWritesHeadersMeanAndSumRows() throws Exception {
        ReportService service = reportService();
        ReportResponse report = report(
                ReportType.DAILY_MEASUREMENT_AVERAGES,
                List.of(row(Map.of(
                        "stationName", "ITE A",
                        "date", LocalDate.of(2026, 7, 1),
                        "rawPhAverage", new BigDecimal("6.500"),
                        "treatedPhAverage", new BigDecimal("7.200"),
                        "workedHours", new BigDecimal("9.50"),
                        "flowTotal", new BigDecimal("950.000"),
                        "flowPerHour", new BigDecimal("100.000"),
                        "flowAverage", new BigDecimal("100.000")
                )))
        );

        byte[] content = service.toXlsx(report);

        try (XSSFWorkbook workbook = openWorkbook(content)) {
            Sheet sheet = workbook.getSheetAt(0);
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("DATA");
            assertThat(sheet.getRow(0).getCell(3).getStringCellValue()).isEqualTo("HORAS TRABALHADAS");
            assertThat(sheet.getRow(0).getCell(6)).isNull();
            assertThat(sheet.getRow(1).getCell(3).getNumericCellValue()).isEqualTo(9.5 / 24.0);
            assertThat(sheet.getRow(1).getCell(6)).isNull();
            assertThat(sheet.getRow(2).getCell(0).getStringCellValue()).isEqualTo("MÉDIA");
            assertThat(sheet.getRow(3).getCell(0).getStringCellValue()).isEqualTo("SOMA");
            assertThat(sheet.getRow(3).getCell(3).getNumericCellValue()).isEqualTo(9.5 / 24.0);
            assertThat(sheet.getRow(3).getCell(4).getNumericCellValue()).isEqualTo(950.0);
            assertThat(sheet.getRow(3).getCell(6)).isNull();
        }
    }

    @Test
    void xlsxExportKeepsHeadersWhenReportHasNoRows() throws Exception {
        ReportService service = reportService();
        ReportResponse report = report(ReportType.LIME, List.of());

        byte[] content = service.toXlsx(report);

        try (XSSFWorkbook workbook = openWorkbook(content)) {
            Sheet sheet = workbook.getSheetAt(0);
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Data");
            assertThat(sheet.getRow(0).getCell(2).getStringCellValue()).isEqualTo("Quantidade inicial");
            assertThat(sheet.getRow(1)).isNull();
        }
    }

    private ReportResponse report(ReportType type, List<ReportRowResponse> rows) {
        return new ReportResponse(
                type,
                Instant.parse("2026-07-01T03:00:00Z"),
                Instant.parse("2026-07-02T03:00:00Z"),
                null,
                true,
                rows
        );
    }

    private List<ReportRowResponse> sampleRows(ReportType type) {
        return switch (type) {
            case WORKED_HOURS -> List.of(row(Map.of(
                    "stationName", "ITE A",
                    "date", LocalDate.of(2026, 7, 1),
                    "workedHours", new BigDecimal("20.00"),
                    "downtimeHours", new BigDecimal("4.00")
            )));
            case LIME -> List.of(row(Map.of(
                    "stationName", "ITE A",
                    "quantityInitial", new BigDecimal("10.00"),
                    "movementType", "Entrada",
                    "quantityMoved", new BigDecimal("4.00"),
                    "quantityFinal", new BigDecimal("14.00"),
                    "date", LocalDate.of(2026, 7, 1),
                    "recordedAt", Instant.parse("2026-07-01T12:00:00Z")
            )));
            case FLOW_MIN_MAX -> List.of(row(Map.of(
                    "stationName", "ITE A",
                    "date", LocalDate.of(2026, 7, 1),
                    "minFlowLiters", new BigDecimal("80.00"),
                    "maxFlowLiters", new BigDecimal("150.00")
            )));
            case PH -> List.of(row(Map.of(
                    "stationName", "ITE A",
                    "waterType", "Entrada",
                    "ph", new BigDecimal("6.80"),
                    "measuredAt", Instant.parse("2026-07-01T12:00:00Z")
            )));
            case FLOW -> List.of(row(Map.of(
                    "stationName", "ITE A",
                    "flowLiters", new BigDecimal("120.50"),
                    "measuredAt", Instant.parse("2026-07-01T12:00:00Z")
            )));
            case STOPPAGES -> List.of(row(Map.of(
                    "stationName", "ITE A",
                    "date", LocalDate.of(2026, 7, 1),
                    "shift", "Dia",
                    "startAt", Instant.parse("2026-07-01T10:00:00Z"),
                    "endAt", Instant.parse("2026-07-01T11:00:00Z"),
                    "durationHours", new BigDecimal("1.00"),
                    "reason", "Falta de água",
                    "status", "Finalizada"
            )));
            case DAILY_MEASUREMENT_AVERAGES -> List.of(row(Map.of(
                    "stationName", "ITE A",
                    "date", LocalDate.of(2026, 7, 1),
                    "rawPhAverage", new BigDecimal("6.500"),
                    "treatedPhAverage", new BigDecimal("7.200"),
                    "workedHours", new BigDecimal("9.50"),
                    "flowTotal", new BigDecimal("950.000"),
                    "flowPerHour", new BigDecimal("100.000"),
                    "flowAverage", new BigDecimal("100.000")
            )));
        };
    }

    private ReportRowResponse row(Map<String, Object> values) {
        return new ReportRowResponse(values);
    }

    private XSSFWorkbook openWorkbook(byte[] content) throws Exception {
        return new XSSFWorkbook(new ByteArrayInputStream(content));
    }

    private ReportService reportService() {
        return new ReportService(
                mock(PhMeasurementRepository.class),
                mock(FlowMeasurementRepository.class),
                mock(LimeRecordRepository.class),
                mock(StoppageService.class),
                mock(StationService.class),
                mock(StationScopeService.class),
                mock(ShiftCalculator.class)
        );
    }

    private ReportService reportService(
            List<Station> stations,
            AppUser currentUser,
            FlowMeasurementRepository flowMeasurementRepository
    ) {
        return reportService(
                stations,
                currentUser,
                flowMeasurementRepository,
                mock(StoppageService.class)
        );
    }

    private ReportService reportService(
            List<Station> stations,
            AppUser currentUser,
            FlowMeasurementRepository flowMeasurementRepository,
            StoppageService stoppageService
    ) {
        return reportService(
                stations,
                currentUser,
                mock(PhMeasurementRepository.class),
                flowMeasurementRepository,
                stoppageService
        );
    }

    private ReportService reportService(
            List<Station> stations,
            AppUser currentUser,
            PhMeasurementRepository phMeasurementRepository,
            FlowMeasurementRepository flowMeasurementRepository,
            StoppageService stoppageService
    ) {
        return reportService(
                stations,
                currentUser,
                phMeasurementRepository,
                flowMeasurementRepository,
                mock(LimeRecordRepository.class),
                stoppageService
        );
    }

    private ReportService reportService(
            List<Station> stations,
            AppUser currentUser,
            PhMeasurementRepository phMeasurementRepository,
            FlowMeasurementRepository flowMeasurementRepository,
            LimeRecordRepository limeRecordRepository,
            StoppageService stoppageService
    ) {
        StationService stationService = new FakeStationService(stations);
        StationScopeService stationScopeService = new StationScopeService(
                stationService,
                new FakeCurrentUserService(currentUser)
        );
        return new ReportService(
                phMeasurementRepository,
                flowMeasurementRepository,
                limeRecordRepository,
                stoppageService,
                stationService,
                stationScopeService,
                mock(ShiftCalculator.class)
        );
    }

    private AppUser user(UserRole role, Station station) {
        return new AppUser("User", role.name().toLowerCase(), "hash", role, station, false);
    }

    private Station station(UUID stationId, String name) {
        Station station = new Station(name, name.toLowerCase().replace(" ", "-"));
        ReflectionTestUtils.setField(station, "id", stationId);
        return station;
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
