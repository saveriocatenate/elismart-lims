package it.elismart_lims.service.io;

import it.elismart_lims.dto.ExperimentResponse;
import it.elismart_lims.dto.MeasurementPairResponse;
import it.elismart_lims.dto.ProtocolResponse;
import it.elismart_lims.dto.UsedReagentBatchResponse;
import it.elismart_lims.service.ExperimentService;
import it.elismart_lims.service.ProtocolService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Generates XLSX exports for single experiments and batch experiment sets.
 *
 * <p>Single-experiment workbook structure:
 * <ol>
 *   <li><b>Summary</b> — metadata, protocol limits, fitted curve parameters.</li>
 *   <li><b>Raw Data</b> — full MeasurementPair table with auto-filter; %CV cells coloured
 *       red when the value exceeds the protocol's {@code maxCvAllowed}.</li>
 *   <li><b>Reagent Batches</b> — lot traceability table.</li>
 * </ol>
 *
 * <p>Batch workbook structure: one {@code Summary_<name>} sheet per experiment plus a
 * single consolidated {@code Raw Data} sheet that prepends an {@code Experiment} column.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExcelExportService {

    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /** Maximum length of an Excel sheet name. */
    private static final int MAX_SHEET_NAME_LENGTH = 31;

    private final ExperimentService experimentService;
    private final ProtocolService protocolService;

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Exports a single experiment to an XLSX byte array (3 sheets).
     *
     * @param experimentId the ID of the experiment to export
     * @return raw XLSX bytes
     * @throws IllegalStateException if generation fails
     */
    public byte[] exportExperiment(Long experimentId) {
        log.info("Generating Excel export for experiment id={}", experimentId);
        ExperimentResponse experiment = experimentService.getById(experimentId);
        ProtocolResponse protocol = protocolService.getByName(experiment.protocolName());

        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            Styles styles = new Styles(workbook);
            writeSummarySheet(workbook, styles, experiment, protocol, "Summary");
            writeRawDataSheet(workbook, styles, experiment.measurementPairs(), protocol.maxCvAllowed());
            writeReagentBatchesSheet(workbook, styles, experiment.usedReagentBatches());

            workbook.write(baos);
            byte[] bytes = baos.toByteArray();
            log.info("Excel export generated for experiment id={} — {} bytes", experimentId, bytes.length);
            return bytes;

        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to generate Excel for experiment id=" + experimentId + ": " + e.getMessage(), e);
        }
    }

    /**
     * Exports multiple experiments to a single XLSX byte array.
     *
     * <p>One {@code Summary_<name>} sheet is written per experiment, followed by a
     * single consolidated {@code Raw Data} sheet containing all pairs across experiments.
     *
     * @param experimentIds the IDs of the experiments to export
     * @return raw XLSX bytes
     * @throws IllegalStateException if generation fails
     */
    public byte[] exportBatch(List<Long> experimentIds) {
        log.info("Generating batch Excel export for {} experiments", experimentIds.size());

        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            Styles styles = new Styles(workbook);

            List<ExperimentEntry> entries = experimentIds.stream()
                    .map(id -> {
                        ExperimentResponse exp = experimentService.getById(id);
                        ProtocolResponse protocol = protocolService.getByName(exp.protocolName());
                        return new ExperimentEntry(exp, protocol);
                    })
                    .toList();

            for (ExperimentEntry entry : entries) {
                String sheetName = truncateSheetName("Summary_" + entry.experiment().name());
                writeSummarySheet(workbook, styles, entry.experiment(), entry.protocol(), sheetName);
            }

            writeConsolidatedRawDataSheet(workbook, styles, entries);

            workbook.write(baos);
            byte[] bytes = baos.toByteArray();
            log.info("Batch Excel export generated for {} experiments — {} bytes",
                    experimentIds.size(), bytes.length);
            return bytes;

        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to generate batch Excel export: " + e.getMessage(), e);
        }
    }

    // -------------------------------------------------------------------------
    // Sheet writers
    // -------------------------------------------------------------------------

    /**
     * Writes the Summary sheet with experiment metadata, protocol limits, and curve parameters.
     *
     * @param workbook  the target workbook
     * @param styles    pre-built cell styles
     * @param exp       the experiment response DTO
     * @param protocol  the protocol response DTO
     * @param sheetName the name to assign to the sheet
     */
    private void writeSummarySheet(Workbook workbook, Styles styles,
                                   ExperimentResponse exp, ProtocolResponse protocol,
                                   String sheetName) {
        Sheet sheet = workbook.createSheet(sheetName);
        sheet.setColumnWidth(0, 9000);
        sheet.setColumnWidth(1, 14000);

        int row = 0;

        row = writeSectionHeader(sheet, styles, row, "Experiment Metadata");
        row = writeMetaRow(sheet, styles, row, "Name", exp.name());
        row = writeMetaRow(sheet, styles, row, "Date",
                exp.date() != null ? exp.date().format(DATETIME_FMT) : "—");
        row = writeMetaRow(sheet, styles, row, "Protocol", exp.protocolName());
        row = writeMetaRow(sheet, styles, row, "Curve Type",
                exp.protocolCurveType() != null ? exp.protocolCurveType().name() : "—");
        row = writeMetaRow(sheet, styles, row, "Status",
                exp.status() != null ? exp.status().name() : "—");
        row = writeMetaRow(sheet, styles, row, "Operator (Created By)",
                exp.createdBy() != null ? exp.createdBy() : "—");
        row++; // blank separator

        row = writeSectionHeader(sheet, styles, row, "Protocol Limits");
        row = writeMetaRow(sheet, styles, row, "Max %CV Allowed",
                protocol.maxCvAllowed() != null
                        ? String.format("%.2f%%", protocol.maxCvAllowed()) : "—");
        row = writeMetaRow(sheet, styles, row, "Max %Error Allowed",
                protocol.maxErrorAllowed() != null
                        ? String.format("%.2f%%", protocol.maxErrorAllowed()) : "—");
        row++; // blank separator

        row = writeSectionHeader(sheet, styles, row, "Calibration Curve Parameters");
        writeMetaRow(sheet, styles, row, "Fitted Parameters (JSON)",
                (exp.curveParameters() != null && !exp.curveParameters().isBlank())
                        ? exp.curveParameters()
                        : "Not yet fitted (experiment not validated)");
    }

    /**
     * Writes the {@code Raw Data} sheet for a single experiment.
     *
     * <p>Columns: Type | Signal 1 | Signal 2 | Mean | %CV | Nom. Conc. | %Recovery | Outlier.
     * Auto-filter is enabled on the header row. %CV cells exceeding {@code maxCvAllowed}
     * are filled with a red background.
     *
     * @param workbook     the target workbook
     * @param styles       pre-built cell styles
     * @param pairs        the measurement pairs to write
     * @param maxCvAllowed the %CV threshold above which the cell is coloured red
     */
    private void writeRawDataSheet(Workbook workbook, Styles styles,
                                   List<MeasurementPairResponse> pairs,
                                   Double maxCvAllowed) {
        Sheet sheet = workbook.createSheet("Raw Data");
        sheet.setColumnWidth(0, 4500);
        sheet.setColumnWidth(1, 4000);
        sheet.setColumnWidth(2, 4000);
        sheet.setColumnWidth(3, 4000);
        sheet.setColumnWidth(4, 4000);
        sheet.setColumnWidth(5, 4500);
        sheet.setColumnWidth(6, 4000);
        sheet.setColumnWidth(7, 3500);

        Row header = sheet.createRow(0);
        createHeaderCell(header, 0, "Type", styles);
        createHeaderCell(header, 1, "Signal 1", styles);
        createHeaderCell(header, 2, "Signal 2", styles);
        createHeaderCell(header, 3, "Mean", styles);
        createHeaderCell(header, 4, "%CV", styles);
        createHeaderCell(header, 5, "Nom. Conc.", styles);
        createHeaderCell(header, 6, "%Recovery", styles);
        createHeaderCell(header, 7, "Outlier", styles);

        if (pairs == null || pairs.isEmpty()) return;

        sheet.setAutoFilter(new CellRangeAddress(0, 0, 0, 7));

        int rowIdx = 1;
        for (MeasurementPairResponse pair : pairs) {
            Row row = sheet.createRow(rowIdx++);
            row.createCell(0).setCellValue(pair.pairType() != null ? pair.pairType().name() : "");
            setNumericCell(row, 1, pair.signal1());
            setNumericCell(row, 2, pair.signal2());
            setNumericCell(row, 3, pair.signalMean());
            setCvCell(row, 4, pair.cvPct(), maxCvAllowed, styles);
            setNumericCell(row, 5, pair.concentrationNominal());
            setNumericCell(row, 6, pair.recoveryPct());
            row.createCell(7).setCellValue(Boolean.TRUE.equals(pair.isOutlier()) ? "YES" : "NO");
        }
    }

    /**
     * Writes the consolidated {@code Raw Data} sheet for a batch export.
     *
     * <p>Prepends an {@code Experiment} column to identify the source experiment for each row.
     * %CV cells are coloured using the corresponding experiment's protocol limit.
     *
     * @param workbook the target workbook
     * @param styles   pre-built cell styles
     * @param entries  list of experiment+protocol pairs
     */
    private void writeConsolidatedRawDataSheet(Workbook workbook, Styles styles,
                                               List<ExperimentEntry> entries) {
        Sheet sheet = workbook.createSheet("Raw Data");
        sheet.setColumnWidth(0, 8000);
        sheet.setColumnWidth(1, 4500);
        sheet.setColumnWidth(2, 4000);
        sheet.setColumnWidth(3, 4000);
        sheet.setColumnWidth(4, 4000);
        sheet.setColumnWidth(5, 4000);
        sheet.setColumnWidth(6, 4500);
        sheet.setColumnWidth(7, 4000);
        sheet.setColumnWidth(8, 3500);

        Row header = sheet.createRow(0);
        createHeaderCell(header, 0, "Experiment", styles);
        createHeaderCell(header, 1, "Type", styles);
        createHeaderCell(header, 2, "Signal 1", styles);
        createHeaderCell(header, 3, "Signal 2", styles);
        createHeaderCell(header, 4, "Mean", styles);
        createHeaderCell(header, 5, "%CV", styles);
        createHeaderCell(header, 6, "Nom. Conc.", styles);
        createHeaderCell(header, 7, "%Recovery", styles);
        createHeaderCell(header, 8, "Outlier", styles);

        int rowIdx = 1;
        for (ExperimentEntry entry : entries) {
            if (entry.experiment().measurementPairs() == null) continue;
            Double maxCv = entry.protocol().maxCvAllowed();
            for (MeasurementPairResponse pair : entry.experiment().measurementPairs()) {
                Row row = sheet.createRow(rowIdx++);
                row.createCell(0).setCellValue(entry.experiment().name());
                row.createCell(1).setCellValue(pair.pairType() != null ? pair.pairType().name() : "");
                setNumericCell(row, 2, pair.signal1());
                setNumericCell(row, 3, pair.signal2());
                setNumericCell(row, 4, pair.signalMean());
                setCvCell(row, 5, pair.cvPct(), maxCv, styles);
                setNumericCell(row, 6, pair.concentrationNominal());
                setNumericCell(row, 7, pair.recoveryPct());
                row.createCell(8).setCellValue(Boolean.TRUE.equals(pair.isOutlier()) ? "YES" : "NO");
            }
        }

        if (rowIdx > 1) {
            sheet.setAutoFilter(new CellRangeAddress(0, 0, 0, 8));
        }
    }

    /**
     * Writes the {@code Reagent Batches} sheet with auto-filter.
     *
     * @param workbook the target workbook
     * @param styles   pre-built cell styles
     * @param batches  the reagent batch DTOs
     */
    private void writeReagentBatchesSheet(Workbook workbook, Styles styles,
                                          List<UsedReagentBatchResponse> batches) {
        Sheet sheet = workbook.createSheet("Reagent Batches");
        sheet.setColumnWidth(0, 9000);
        sheet.setColumnWidth(1, 6000);
        sheet.setColumnWidth(2, 6000);

        Row header = sheet.createRow(0);
        createHeaderCell(header, 0, "Reagent Name", styles);
        createHeaderCell(header, 1, "Lot Number", styles);
        createHeaderCell(header, 2, "Expiry Date", styles);

        if (batches == null || batches.isEmpty()) return;

        sheet.setAutoFilter(new CellRangeAddress(0, 0, 0, 2));

        int rowIdx = 1;
        for (UsedReagentBatchResponse batch : batches) {
            Row row = sheet.createRow(rowIdx++);
            row.createCell(0).setCellValue(batch.reagentName() != null ? batch.reagentName() : "");
            row.createCell(1).setCellValue(batch.lotNumber() != null ? batch.lotNumber() : "");
            row.createCell(2).setCellValue(
                    batch.expiryDate() != null ? batch.expiryDate().format(DATE_FMT) : "—");
        }
    }

    // -------------------------------------------------------------------------
    // Row / cell helpers
    // -------------------------------------------------------------------------

    /**
     * Writes a bold section-header row and returns the next row index.
     *
     * @param sheet  the target sheet
     * @param styles pre-built cell styles
     * @param rowIdx the row index to write on
     * @param title  the header text
     * @return {@code rowIdx + 1}
     */
    private int writeSectionHeader(Sheet sheet, Styles styles, int rowIdx, String title) {
        Row row = sheet.createRow(rowIdx);
        Cell cell = row.createCell(0);
        cell.setCellValue(title);
        cell.setCellStyle(styles.sectionHeader);
        return rowIdx + 1;
    }

    /**
     * Writes a two-column label/value metadata row and returns the next row index.
     *
     * @param sheet  the target sheet
     * @param styles pre-built cell styles
     * @param rowIdx the row index to write on
     * @param label  the label text
     * @param value  the value text; {@code null} becomes "—"
     * @return {@code rowIdx + 1}
     */
    private int writeMetaRow(Sheet sheet, Styles styles, int rowIdx, String label, String value) {
        Row row = sheet.createRow(rowIdx);
        Cell labelCell = row.createCell(0);
        labelCell.setCellValue(label);
        labelCell.setCellStyle(styles.label);
        Cell valueCell = row.createCell(1);
        valueCell.setCellValue(value != null ? value : "—");
        valueCell.setCellStyle(styles.value);
        return rowIdx + 1;
    }

    /**
     * Creates a bold, centred header cell with grey fill.
     *
     * @param row    the target row
     * @param col    the column index
     * @param text   the header text
     * @param styles pre-built cell styles
     */
    private void createHeaderCell(Row row, int col, String text, Styles styles) {
        Cell cell = row.createCell(col);
        cell.setCellValue(text);
        cell.setCellStyle(styles.tableHeader);
    }

    /**
     * Sets a numeric cell value; leaves the cell blank if {@code value} is {@code null}.
     *
     * @param row   the target row
     * @param col   the column index
     * @param value the numeric value, or {@code null}
     */
    private void setNumericCell(Row row, int col, Double value) {
        Cell cell = row.createCell(col);
        if (value != null) cell.setCellValue(value);
    }

    /**
     * Sets a %CV cell, applying a red fill style when the value exceeds {@code maxCvAllowed}.
     *
     * @param row          the target row
     * @param col          the column index
     * @param cvPct        the %CV value, or {@code null}
     * @param maxCvAllowed the protocol limit above which the cell is flagged; may be {@code null}
     * @param styles       pre-built cell styles
     */
    private void setCvCell(Row row, int col, Double cvPct, Double maxCvAllowed, Styles styles) {
        Cell cell = row.createCell(col);
        if (cvPct == null) return;
        cell.setCellValue(cvPct);
        if (maxCvAllowed != null && cvPct > maxCvAllowed) {
            cell.setCellStyle(styles.cvFail);
        } else {
            cell.setCellStyle(styles.numeric);
        }
    }

    /**
     * Truncates a string to {@value #MAX_SHEET_NAME_LENGTH} characters for use as an Excel sheet name.
     *
     * @param name the candidate sheet name
     * @return the name, truncated if necessary
     */
    private String truncateSheetName(String name) {
        return name.length() > MAX_SHEET_NAME_LENGTH ? name.substring(0, MAX_SHEET_NAME_LENGTH) : name;
    }

    // -------------------------------------------------------------------------
    // Inner types
    // -------------------------------------------------------------------------

    /**
     * Pre-built {@link CellStyle} instances shared across the workbook to stay within
     * the 64 k style-per-workbook limit.
     */
    private static final class Styles {

        final CellStyle sectionHeader;
        final CellStyle label;
        final CellStyle value;
        final CellStyle tableHeader;
        final CellStyle numeric;
        final CellStyle cvFail;

        /**
         * Creates all styles in the given workbook.
         *
         * @param wb the workbook in which to create styles and fonts
         */
        Styles(Workbook wb) {
            DataFormat fmt = wb.createDataFormat();

            sectionHeader = wb.createCellStyle();
            Font sectionFont = wb.createFont();
            sectionFont.setBold(true);
            sectionFont.setFontHeightInPoints((short) 12);
            sectionHeader.setFont(sectionFont);
            sectionHeader.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            sectionHeader.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            label = wb.createCellStyle();
            Font labelFont = wb.createFont();
            labelFont.setBold(true);
            label.setFont(labelFont);
            label.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
            label.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            value = wb.createCellStyle();

            tableHeader = wb.createCellStyle();
            Font headerFont = wb.createFont();
            headerFont.setBold(true);
            tableHeader.setFont(headerFont);
            tableHeader.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            tableHeader.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            tableHeader.setAlignment(HorizontalAlignment.CENTER);

            numeric = wb.createCellStyle();
            numeric.setDataFormat(fmt.getFormat("0.00"));

            cvFail = wb.createCellStyle();
            cvFail.setDataFormat(fmt.getFormat("0.00"));
            cvFail.setFillForegroundColor(IndexedColors.RED1.getIndex());
            cvFail.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        }
    }

    /**
     * Pairs an {@link ExperimentResponse} with its resolved {@link ProtocolResponse}
     * to avoid redundant service calls during batch processing.
     *
     * @param experiment the experiment response DTO
     * @param protocol   the corresponding protocol response DTO
     */
    private record ExperimentEntry(ExperimentResponse experiment, ProtocolResponse protocol) {
    }
}
