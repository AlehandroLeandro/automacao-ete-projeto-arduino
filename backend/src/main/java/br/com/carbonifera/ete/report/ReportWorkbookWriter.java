package br.com.carbonifera.ete.report;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.WorkbookUtil;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

final class ReportWorkbookWriter {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("America/Sao_Paulo");
    private static final MathContext AVERAGE_CONTEXT = MathContext.DECIMAL64;
    private static final String STATION_KEY = "stationName";

    private ReportWorkbookWriter() {
    }

    // Cada tipo escreve suas planilhas por ITE (consolidado = uma aba por estação; ITE específica = uma aba).
    static byte[] write(ReportResponse report) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            WorkbookStyles styles = WorkbookStyles.create(workbook);
            writeStationSheets(workbook, report, styles, sheetWriter(report.type()));
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not generate XLSX report", exception);
        }
    }

    private static SheetWriter sheetWriter(ReportType type) {
        return switch (type) {
            case WORKED_HOURS -> ReportWorkbookWriter::writeWorkedHoursSheet;
            case LIME -> ReportWorkbookWriter::writeLimeSheet;
            case FLOW_MIN_MAX -> ReportWorkbookWriter::writeFlowMinMaxSheet;
            case PH -> ReportWorkbookWriter::writePhSheet;
            case FLOW -> ReportWorkbookWriter::writeFlowSheet;
            case STOPPAGES -> ReportWorkbookWriter::writeStoppagesSheet;
            case DAILY_MEASUREMENT_AVERAGES -> ReportWorkbookWriter::writeDailyAveragesSheet;
        };
    }

    private static void writeStationSheets(
            Workbook workbook,
            ReportResponse report,
            WorkbookStyles styles,
            SheetWriter writer
    ) {
        Map<String, List<ReportRowResponse>> byStation = groupByStation(report.rows());
        Map<String, Integer> usedNames = new LinkedHashMap<>();

        if (byStation.isEmpty()) {
            Sheet sheet = workbook.createSheet(uniqueSheetName(reportLabel(report.type()), usedNames));
            writer.write(sheet, List.of(), styles);
            return;
        }

        for (Map.Entry<String, List<ReportRowResponse>> entry : byStation.entrySet()) {
            Sheet sheet = workbook.createSheet(uniqueSheetName(entry.getKey(), usedNames));
            writer.write(sheet, entry.getValue(), styles);
        }
    }

    // Agrupa as linhas por ITE preservando a ordem; estações sem linhas não geram aba (decisão de Design).
    private static Map<String, List<ReportRowResponse>> groupByStation(List<ReportRowResponse> rows) {
        return rows.stream()
                .collect(Collectors.groupingBy(
                        row -> Objects.toString(row.values().get(STATION_KEY), "Relatorio"),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
    }

    private static void writeWorkedHoursSheet(Sheet sheet, List<ReportRowResponse> rows, WorkbookStyles styles) {
        Row header = sheet.createRow(0);
        writeText(header, 0, "Data", styles.header());
        writeText(header, 1, "Horas trabalhadas", styles.header());
        writeText(header, 2, "Horas paradas", styles.header());

        int rowIndex = 1;
        BigDecimal totalWorked = BigDecimal.ZERO;
        BigDecimal totalDowntime = BigDecimal.ZERO;
        for (ReportRowResponse row : rows) {
            Map<String, Object> values = row.values();
            Row sheetRow = sheet.createRow(rowIndex);
            writeDate(sheetRow, 0, values.get("date"), styles.date());
            writeHours(sheetRow, 1, values.get("workedHours"), styles.timeHours());
            writeHours(sheetRow, 2, values.get("downtimeHours"), styles.timeHours());
            totalWorked = addNumber(totalWorked, values.get("workedHours"));
            totalDowntime = addNumber(totalDowntime, values.get("downtimeHours"));
            rowIndex += 1;
        }

        if (!rows.isEmpty()) {
            Row totalRow = sheet.createRow(rowIndex);
            writeText(totalRow, 0, "TOTAL", styles.average());
            writeHours(totalRow, 1, totalWorked, styles.averageTime());
            writeHours(totalRow, 2, totalDowntime, styles.averageTime());
        }

        autoSizeColumns(sheet, 3);
    }

    private static void writeLimeSheet(Sheet sheet, List<ReportRowResponse> rows, WorkbookStyles styles) {
        Row header = sheet.createRow(0);
        writeText(header, 0, "Data", styles.header());
        writeText(header, 1, "Horário", styles.header());
        writeText(header, 2, "Quantidade inicial", styles.header());
        writeText(header, 3, "Tipo de movimento", styles.header());
        writeText(header, 4, "Quantidade movimentada", styles.header());
        writeText(header, 5, "Quantidade final", styles.header());

        int rowIndex = 1;
        for (ReportRowResponse row : rows) {
            Map<String, Object> values = row.values();
            Row sheetRow = sheet.createRow(rowIndex);
            writeDate(sheetRow, 0, values.get("date"), styles.date());
            writeTime(sheetRow, 1, values.get("recordedAt"), styles.time());
            writeNumber(sheetRow, 2, values.get("quantityInitial"), styles.number());
            writeText(sheetRow, 3, readableText(values.get("movementType")), styles.body());
            writeNumber(sheetRow, 4, values.get("quantityMoved"), styles.number());
            writeNumber(sheetRow, 5, values.get("quantityFinal"), styles.number());
            rowIndex += 1;
        }

        autoSizeColumns(sheet, 6);
    }

    private static void writeFlowMinMaxSheet(Sheet sheet, List<ReportRowResponse> rows, WorkbookStyles styles) {
        Row header = sheet.createRow(0);
        writeText(header, 0, "Data", styles.header());
        writeText(header, 1, "Vazão mínima", styles.header());
        writeText(header, 2, "Vazão máxima", styles.header());

        int rowIndex = 1;
        for (ReportRowResponse row : rows) {
            Map<String, Object> values = row.values();
            Row sheetRow = sheet.createRow(rowIndex);
            writeDate(sheetRow, 0, values.get("date"), styles.date());
            writeNumber(sheetRow, 1, values.get("minFlowLiters"), styles.number());
            writeNumber(sheetRow, 2, values.get("maxFlowLiters"), styles.number());
            rowIndex += 1;
        }

        autoSizeColumns(sheet, 3);
    }

    // pH: duas tabelas lado a lado na mesma aba (entrada e saída).
    private static void writePhSheet(Sheet sheet, List<ReportRowResponse> rows, WorkbookStyles styles) {
        List<ReportRowResponse> entryRows = phRowsOf(rows, "Entrada");
        List<ReportRowResponse> outputRows = phRowsOf(rows, "Saída");

        writePhTable(sheet, "pH de entrada", entryRows, 0, 0, styles);
        writePhTable(sheet, "pH de saída", outputRows, 0, 3, styles);

        autoSizeColumns(sheet, 5);
    }

    private static List<ReportRowResponse> phRowsOf(List<ReportRowResponse> rows, String waterType) {
        return rows.stream()
                .filter(row -> waterType.equals(Objects.toString(row.values().get("waterType"), "")))
                .toList();
    }

    private static int writePhTable(
            Sheet sheet,
            String title,
            List<ReportRowResponse> rows,
            int startRow,
            int startColumn,
            WorkbookStyles styles
    ) {
        Row titleRow = rowAt(sheet, startRow);
        writeText(titleRow, startColumn, title, styles.title());
        writeText(titleRow, startColumn + 1, "", styles.title());
        sheet.addMergedRegion(new CellRangeAddress(startRow, startRow, startColumn, startColumn + 1));

        Row headerRow = rowAt(sheet, startRow + 1);
        writeText(headerRow, startColumn, "pH", styles.header());
        writeText(headerRow, startColumn + 1, "Coletado em", styles.header());

        int rowIndex = startRow + 2;
        for (ReportRowResponse row : rows) {
            Map<String, Object> values = row.values();
            Row sheetRow = rowAt(sheet, rowIndex);
            writeNumber(sheetRow, startColumn, values.get("ph"), styles.number());
            writeDateTime(sheetRow, startColumn + 1, values.get("measuredAt"), styles.dateTime());
            rowIndex += 1;
        }

        return rowIndex;
    }

    // Vazão: todos os registros e a média do período ao fim da coluna de vazão.
    private static void writeFlowSheet(Sheet sheet, List<ReportRowResponse> rows, WorkbookStyles styles) {
        Row header = sheet.createRow(0);
        writeText(header, 0, "Coletado em", styles.header());
        writeText(header, 1, "Vazão", styles.header());

        int rowIndex = 1;
        BigDecimal total = BigDecimal.ZERO;
        int count = 0;
        for (ReportRowResponse row : rows) {
            Map<String, Object> values = row.values();
            Row sheetRow = sheet.createRow(rowIndex);
            writeDateTime(sheetRow, 0, values.get("measuredAt"), styles.dateTime());
            writeNumber(sheetRow, 1, values.get("flowLiters"), styles.number());
            total = addNumber(total, values.get("flowLiters"));
            count += 1;
            rowIndex += 1;
        }

        if (!rows.isEmpty()) {
            Row averageRow = sheet.createRow(rowIndex);
            BigDecimal average = total.divide(BigDecimal.valueOf(count), AVERAGE_CONTEXT).setScale(3, RoundingMode.HALF_UP);
            writeText(averageRow, 0, "MÉDIA", styles.average());
            writeNumber(averageRow, 1, average, styles.averageNumber());
        }

        autoSizeColumns(sheet, 2);
    }

    private static void writeStoppagesSheet(Sheet sheet, List<ReportRowResponse> rows, WorkbookStyles styles) {
        Row header = sheet.createRow(0);
        writeText(header, 0, "Data", styles.header());
        writeText(header, 1, "Turno", styles.header());
        writeText(header, 2, "Horário de início", styles.header());
        writeText(header, 3, "Horário de fim", styles.header());
        writeText(header, 4, "Duração", styles.header());
        writeText(header, 5, "Motivo", styles.header());
        writeText(header, 6, "Status", styles.header());

        int rowIndex = 1;
        for (ReportRowResponse row : rows) {
            Map<String, Object> values = row.values();
            Row sheetRow = sheet.createRow(rowIndex);
            writeDate(sheetRow, 0, values.get("date"), styles.date());
            writeText(sheetRow, 1, readableText(values.get("shift")), styles.body());
            writeTime(sheetRow, 2, values.get("startAt"), styles.time());
            writeTime(sheetRow, 3, values.get("endAt"), styles.time());
            writeHours(sheetRow, 4, values.get("durationHours"), styles.timeHours());
            writeText(sheetRow, 5, readableText(values.get("reason")), styles.body());
            writeText(sheetRow, 6, readableText(values.get("status")), styles.body());
            rowIndex += 1;
        }

        autoSizeColumns(sheet, 7);
    }

    // Médias diárias com linha de média e, abaixo, a soma de horas trabalhadas e de vazão do período.
    private static void writeDailyAveragesSheet(Sheet sheet, List<ReportRowResponse> rows, WorkbookStyles styles) {
        Row header = sheet.createRow(0);
        writeText(header, 0, "DATA", styles.header());
        writeText(header, 1, "pH ENTRA", styles.header());
        writeText(header, 2, "pH SAÍDA", styles.header());
        writeText(header, 3, "HORAS TRABALHADAS", styles.header());
        writeText(header, 4, "VAZÃO TOTAL", styles.header());
        writeText(header, 5, "VAZÃO POR HORA", styles.header());

        int rowIndex = 1;
        for (ReportRowResponse row : rows) {
            Map<String, Object> values = row.values();
            Row sheetRow = sheet.createRow(rowIndex);
            writeDate(sheetRow, 0, values.get("date"), styles.date());
            writeNumber(sheetRow, 1, values.get("rawPhAverage"), styles.number());
            writeNumber(sheetRow, 2, values.get("treatedPhAverage"), styles.number());
            writeHours(sheetRow, 3, values.get("workedHours"), styles.timeHours());
            writeNumber(sheetRow, 4, values.get("flowTotal"), styles.number());
            writeNumber(sheetRow, 5, values.get("flowPerHour"), styles.number());
            rowIndex += 1;
        }

        if (!rows.isEmpty()) {
            Row averageRow = sheet.createRow(rowIndex);
            writeText(averageRow, 0, "MÉDIA", styles.average());
            writeNumber(averageRow, 1, periodAverage(rows, "rawPhAverage"), styles.averageNumber());
            writeNumber(averageRow, 2, periodAverage(rows, "treatedPhAverage"), styles.averageNumber());
            writeHours(averageRow, 3, periodAverage(rows, "workedHours"), styles.averageTime());
            writeNumber(averageRow, 4, periodAverage(rows, "flowTotal"), styles.averageNumber());
            writeNumber(averageRow, 5, periodAverage(rows, "flowPerHour"), styles.averageNumber());

            Row sumRow = sheet.createRow(rowIndex + 1);
            writeText(sumRow, 0, "SOMA", styles.average());
            writeBlank(sumRow, 1, styles.average());
            writeBlank(sumRow, 2, styles.average());
            writeHours(sumRow, 3, periodSum(rows, "workedHours"), styles.averageTime());
            writeNumber(sumRow, 4, periodSum(rows, "flowTotal"), styles.averageNumber());
            writeBlank(sumRow, 5, styles.average());
        }

        autoSizeColumns(sheet, 6);
    }

    private static Row rowAt(Sheet sheet, int rowIndex) {
        return Optional.ofNullable(sheet.getRow(rowIndex)).orElseGet(() -> sheet.createRow(rowIndex));
    }

    private static void writeText(Row row, int column, String value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    private static void writeBlank(Row row, int column, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setBlank();
        cell.setCellStyle(style);
    }

    private static void writeNumber(Row row, int column, Object value, CellStyle style) {
        Cell cell = row.createCell(column);
        if (value instanceof BigDecimal number) {
            cell.setCellValue(number.doubleValue());
        } else if (value instanceof Number number) {
            cell.setCellValue(number.doubleValue());
        } else {
            cell.setBlank();
        }
        cell.setCellStyle(style);
    }

    // Horas escritas como fração de dia para o formato [h]:mm do Excel.
    private static void writeHours(Row row, int column, Object value, CellStyle style) {
        Cell cell = row.createCell(column);
        if (value instanceof BigDecimal hours) {
            cell.setCellValue(hours.doubleValue() / 24.0);
        } else if (value instanceof Number hours) {
            cell.setCellValue(hours.doubleValue() / 24.0);
        } else {
            cell.setBlank();
        }
        cell.setCellStyle(style);
    }

    private static void writeDate(Row row, int column, Object value, CellStyle style) {
        Cell cell = row.createCell(column);
        if (value instanceof LocalDate date) {
            cell.setCellValue(date.atStartOfDay());
        } else {
            cell.setBlank();
        }
        cell.setCellStyle(style);
    }

    private static void writeTime(Row row, int column, Object value, CellStyle style) {
        Cell cell = row.createCell(column);
        if (value instanceof Instant instant) {
            cell.setCellValue(LocalDateTime.ofInstant(instant, BUSINESS_ZONE));
        } else {
            cell.setBlank();
        }
        cell.setCellStyle(style);
    }

    private static void writeDateTime(Row row, int column, Object value, CellStyle style) {
        Cell cell = row.createCell(column);
        if (value instanceof Instant instant) {
            cell.setCellValue(LocalDateTime.ofInstant(instant, BUSINESS_ZONE));
        } else {
            cell.setBlank();
        }
        cell.setCellStyle(style);
    }

    private static BigDecimal addNumber(BigDecimal accumulator, Object value) {
        if (value instanceof BigDecimal number) {
            return accumulator.add(number);
        }
        if (value instanceof Number number) {
            return accumulator.add(BigDecimal.valueOf(number.doubleValue()));
        }
        return accumulator;
    }

    private static void autoSizeColumns(Sheet sheet, int columns) {
        for (int column = 0; column < columns; column += 1) {
            sheet.autoSizeColumn(column);
            int width = Math.min(Math.max(sheet.getColumnWidth(column), 12 * 256), 32 * 256);
            sheet.setColumnWidth(column, width);
        }
    }

    private static BigDecimal periodAverage(List<ReportRowResponse> rows, String key) {
        List<BigDecimal> values = numericValues(rows, key);
        if (values.isEmpty()) {
            return null;
        }
        BigDecimal total = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return total.divide(BigDecimal.valueOf(values.size()), AVERAGE_CONTEXT).setScale(3, RoundingMode.HALF_UP);
    }

    private static BigDecimal periodSum(List<ReportRowResponse> rows, String key) {
        List<BigDecimal> values = numericValues(rows, key);
        if (values.isEmpty()) {
            return null;
        }
        return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static List<BigDecimal> numericValues(List<ReportRowResponse> rows, String key) {
        return rows.stream()
                .map(row -> row.values().get(key))
                .filter(BigDecimal.class::isInstance)
                .map(BigDecimal.class::cast)
                .toList();
    }

    private static String reportLabel(ReportType type) {
        return switch (type) {
            case WORKED_HOURS -> "Horas trabalhadas";
            case LIME -> "Cal";
            case FLOW_MIN_MAX -> "Vazao minima e maxima";
            case PH -> "pH";
            case FLOW -> "Vazao";
            case STOPPAGES -> "Paradas";
            case DAILY_MEASUREMENT_AVERAGES -> "Medias afericoes";
        };
    }

    private static String uniqueSheetName(String rawName, Map<String, Integer> usedNames) {
        String base = WorkbookUtil.createSafeSheetName(blankToDefault(rawName, "Relatorio")).trim();
        if (base.isBlank()) {
            base = "Relatorio";
        }
        if (base.length() > 31) {
            base = base.substring(0, 31);
        }

        int count = usedNames.merge(base, 1, Integer::sum);
        if (count == 1) {
            return base;
        }

        String suffix = " (" + count + ")";
        int maxBaseLength = 31 - suffix.length();
        return base.substring(0, Math.min(base.length(), maxBaseLength)) + suffix;
    }

    private static String blankToDefault(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }

    private static String readableText(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Enum<?> enumValue) {
            return enumValue.name();
        }
        return value.toString();
    }

    @FunctionalInterface
    private interface SheetWriter {
        void write(Sheet sheet, List<ReportRowResponse> rows, WorkbookStyles styles);
    }

    private record WorkbookStyles(
            CellStyle title,
            CellStyle header,
            CellStyle body,
            CellStyle number,
            CellStyle date,
            CellStyle time,
            CellStyle dateTime,
            CellStyle average,
            CellStyle averageNumber,
            CellStyle timeHours,
            CellStyle averageTime
    ) {

        static WorkbookStyles create(Workbook workbook) {
            CreationHelper creationHelper = workbook.getCreationHelper();

            CellStyle title = bordered(workbook);
            title.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
            title.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            title.setAlignment(HorizontalAlignment.CENTER);

            CellStyle header = bordered(workbook);
            header.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            header.setAlignment(HorizontalAlignment.CENTER);

            CellStyle body = bordered(workbook);

            CellStyle number = bordered(workbook);
            number.setAlignment(HorizontalAlignment.RIGHT);
            number.setDataFormat(creationHelper.createDataFormat().getFormat("#,##0.000"));

            CellStyle date = bordered(workbook);
            date.setDataFormat(creationHelper.createDataFormat().getFormat("dd/mm/yyyy"));

            CellStyle time = bordered(workbook);
            time.setDataFormat(creationHelper.createDataFormat().getFormat("hh:mm"));

            CellStyle dateTime = bordered(workbook);
            dateTime.setDataFormat(creationHelper.createDataFormat().getFormat("dd/mm/yyyy hh:mm"));

            CellStyle average = bordered(workbook);
            average.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            average.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            CellStyle averageNumber = bordered(workbook);
            averageNumber.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            averageNumber.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            averageNumber.setAlignment(HorizontalAlignment.RIGHT);
            averageNumber.setDataFormat(creationHelper.createDataFormat().getFormat("#,##0.000"));

            CellStyle timeHours = bordered(workbook);
            timeHours.setAlignment(HorizontalAlignment.RIGHT);
            timeHours.setDataFormat(creationHelper.createDataFormat().getFormat("[h]:mm"));

            CellStyle averageTime = bordered(workbook);
            averageTime.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            averageTime.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            averageTime.setAlignment(HorizontalAlignment.RIGHT);
            averageTime.setDataFormat(creationHelper.createDataFormat().getFormat("[h]:mm"));

            return new WorkbookStyles(
                    title,
                    header,
                    body,
                    number,
                    date,
                    time,
                    dateTime,
                    average,
                    averageNumber,
                    timeHours,
                    averageTime
            );
        }

        private static CellStyle bordered(Workbook workbook) {
            CellStyle style = workbook.createCellStyle();
            style.setBorderBottom(BorderStyle.THIN);
            style.setBorderLeft(BorderStyle.THIN);
            style.setBorderRight(BorderStyle.THIN);
            style.setBorderTop(BorderStyle.THIN);
            return style;
        }
    }
}
