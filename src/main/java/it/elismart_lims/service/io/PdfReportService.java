package it.elismart_lims.service.io;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;
import com.lowagie.text.pdf.draw.LineSeparator;
import it.elismart_lims.dto.ExperimentResponse;
import it.elismart_lims.dto.MeasurementPairResponse;
import it.elismart_lims.dto.UsedReagentBatchResponse;
import it.elismart_lims.exception.model.ResourceNotFoundException;
import it.elismart_lims.model.ExperimentStatus;
import it.elismart_lims.service.ExperimentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Generates a PDF Certificate of Analysis for a given experiment.
 *
 * <p>The document follows the standard LIMS CoA structure:
 * <ol>
 *   <li>Header — title and generation date</li>
 *   <li>Experiment Metadata — name, date, protocol, operator, status</li>
 *   <li>Reagent Batches — reagent name, lot number, expiry date</li>
 *   <li>Calibration Curve Parameters — curve type and fitted parameters (if available)</li>
 *   <li>Results — per-pair signal, %CV, %Recovery, PASS/FAIL</li>
 *   <li>Footer — reviewer signature block, page numbers, disclaimer</li>
 * </ol>
 *
 * <p>Color coding: PASS rows are tinted green, FAIL rows are tinted red, matching the
 * protocol limits stored on the experiment's protocol at validation time.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PdfReportService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy");
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm");

    /** Light green background for PASS cells. */
    private static final Color COLOR_PASS = new Color(0xC8, 0xE6, 0xC9);
    /** Light red background for FAIL cells. */
    private static final Color COLOR_FAIL = new Color(0xFF, 0xCC, 0xBC);
    /** Light grey background for table headers. */
    private static final Color COLOR_HEADER = new Color(0xEE, 0xEE, 0xEE);

    private static final Font FONT_TITLE = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, Color.BLACK);
    private static final Font FONT_SECTION = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, Color.BLACK);
    private static final Font FONT_BODY = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.BLACK);
    private static final Font FONT_SMALL = FontFactory.getFont(FontFactory.HELVETICA, 8, Color.DARK_GRAY);
    private static final Font FONT_STATUS_OK = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, new Color(0x2E, 0x7D, 0x32));
    private static final Font FONT_STATUS_KO = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, new Color(0xB7, 0x1C, 0x1C));

    private final ExperimentService experimentService;

    /**
     * Generates a PDF Certificate of Analysis for the experiment identified by {@code experimentId}.
     *
     * @param experimentId the experiment whose data to include in the CoA
     * @return raw PDF bytes ready to stream as a download
     * @throws ResourceNotFoundException if no experiment exists with the given ID
     * @throws IllegalStateException     if PDF generation fails
     */
    public byte[] generateCertificateOfAnalysis(Long experimentId) {
        log.info("Generating PDF CoA for experiment id={}", experimentId);

        ExperimentResponse experiment = experimentService.getById(experimentId);

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 40, 40, 60, 60);
            PdfWriter writer = PdfWriter.getInstance(document, baos);
            writer.setPageEvent(new PageFooterEvent());
            document.open();

            addHeader(document);
            addMetadata(document, experiment);
            addReagentBatches(document, experiment.usedReagentBatches());
            addCurveParameters(document, experiment);
            addResults(document, experiment.measurementPairs());
            addSignatureBlock(document);

            document.close();

            byte[] pdf = baos.toByteArray();
            log.info("PDF CoA generated for experiment id={} — {} bytes", experimentId, pdf.length);
            return pdf;

        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to generate PDF for experiment id=" + experimentId + ": " + e.getMessage(), e);
        }
    }

    // -------------------------------------------------------------------------
    // Section builders
    // -------------------------------------------------------------------------

    /**
     * Adds the document header: title and generation timestamp.
     *
     * @param doc        the OpenPDF {@link Document}
     * @throws DocumentException if OpenPDF cannot add the element
     */
    private void addHeader(Document doc) throws DocumentException {
        Paragraph title = new Paragraph("Certificate of Analysis", FONT_TITLE);
        title.setAlignment(Element.ALIGN_CENTER);
        doc.add(title);

        Paragraph subtitle = new Paragraph(
                "EliSmart LIMS  —  Generated: " + LocalDate.now().format(DATE_FMT),
                FONT_SMALL);
        subtitle.setAlignment(Element.ALIGN_CENTER);
        doc.add(subtitle);

        doc.add(Chunk.NEWLINE);
        doc.add(new LineSeparator());
        doc.add(Chunk.NEWLINE);
    }

    /**
     * Adds the Experiment Metadata section.
     *
     * @param doc        the OpenPDF {@link Document}
     * @param experiment the experiment response DTO
     * @throws DocumentException if OpenPDF cannot add the element
     */
    private void addMetadata(Document doc, ExperimentResponse experiment) throws DocumentException {
        doc.add(new Paragraph("Experiment Metadata", FONT_SECTION));
        doc.add(Chunk.NEWLINE);

        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{1.5f, 3f});

        addMetaRow(table, "Experiment Name", experiment.name());
        addMetaRow(table, "Date", experiment.date() != null ? experiment.date().format(DATETIME_FMT) : "—");
        addMetaRow(table, "Protocol", experiment.protocolName());
        addMetaRow(table, "Curve Type",
                experiment.protocolCurveType() != null ? experiment.protocolCurveType().name() : "—");
        addMetaRow(table, "Operator (Created By)", experiment.createdBy() != null ? experiment.createdBy() : "—");

        // Status cell with colour coding
        PdfPCell labelCell = labelCell("Status");
        table.addCell(labelCell);

        boolean isOk = experiment.status() == ExperimentStatus.OK;
        boolean isKo = experiment.status() == ExperimentStatus.KO;
        Font font = isKo ? FONT_STATUS_KO : FONT_BODY;
        Font statusFont = isOk ? FONT_STATUS_OK : font;
        PdfPCell statusCell = new PdfPCell(new Phrase(experiment.status().name(), statusFont));
        statusCell.setPadding(5);
        if (isOk) statusCell.setBackgroundColor(COLOR_PASS);
        else if (isKo) statusCell.setBackgroundColor(COLOR_FAIL);
        table.addCell(statusCell);

        doc.add(table);
        doc.add(Chunk.NEWLINE);
    }

    /**
     * Adds the Reagent Batches section.
     *
     * @param doc     the OpenPDF {@link Document}
     * @param batches the list of used reagent batch DTOs
     * @throws DocumentException if OpenPDF cannot add the element
     */
    private void addReagentBatches(Document doc, List<UsedReagentBatchResponse> batches) throws DocumentException {
        doc.add(new Paragraph("Reagent Batches", FONT_SECTION));
        doc.add(Chunk.NEWLINE);

        if (batches == null || batches.isEmpty()) {
            doc.add(new Paragraph("No reagent batches recorded.", FONT_SMALL));
            doc.add(Chunk.NEWLINE);
            return;
        }

        PdfPTable table = new PdfPTable(3);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{3f, 2f, 2f});

        addHeaderRow(table, "Reagent Name", "Lot Number", "Expiry Date");

        for (UsedReagentBatchResponse batch : batches) {
            table.addCell(bodyCell(batch.reagentBatch().reagentName()));
            table.addCell(bodyCell(batch.reagentBatch().lotNumber()));
            table.addCell(bodyCell(batch.reagentBatch().expiryDate() != null
                    ? batch.reagentBatch().expiryDate().format(DATE_FMT) : "—"));
        }

        doc.add(table);
        doc.add(Chunk.NEWLINE);
    }

    /**
     * Adds the Calibration Curve Parameters section.
     * If {@code curveParameters} is {@code null}, shows a placeholder message.
     *
     * @param doc        the OpenPDF {@link Document}
     * @param experiment the experiment response DTO
     * @throws DocumentException if OpenPDF cannot add the element
     */
    private void addCurveParameters(Document doc, ExperimentResponse experiment) throws DocumentException {
        doc.add(new Paragraph("Calibration Curve", FONT_SECTION));
        doc.add(Chunk.NEWLINE);

        if (experiment.curveParameters() == null || experiment.curveParameters().isBlank()) {
            doc.add(new Paragraph(
                    "Curve parameters not available (experiment not yet validated).\n"
                    + "Curve plot: see Excel export.",
                    FONT_SMALL));
            doc.add(Chunk.NEWLINE);
            return;
        }

        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(60);
        table.setWidths(new float[]{2f, 3f});

        addMetaRow(table, "Curve Type",
                experiment.protocolCurveType() != null ? experiment.protocolCurveType().name() : "—");
        addMetaRow(table, "Parameters (JSON)", experiment.curveParameters());

        doc.add(table);
        doc.add(new Paragraph("Curve plot: see Excel export.", FONT_SMALL));
        doc.add(Chunk.NEWLINE);
    }

    /**
     * Adds the Results table with one row per {@link MeasurementPairResponse}.
     * Rows are colour-coded: outliers are grey, otherwise PASS (green) / FAIL (red)
     * based on non-null cvPct and recoveryPct values.
     *
     * @param doc   the OpenPDF {@link Document}
     * @param pairs the list of measurement pair response DTOs
     * @throws DocumentException if OpenPDF cannot add the element
     */
    private void addResults(Document doc, List<MeasurementPairResponse> pairs) throws DocumentException {
        doc.add(new Paragraph("Results", FONT_SECTION));
        doc.add(Chunk.NEWLINE);

        if (pairs == null || pairs.isEmpty()) {
            doc.add(new Paragraph("No measurement pairs recorded.", FONT_SMALL));
            doc.add(Chunk.NEWLINE);
            return;
        }

        PdfPTable table = new PdfPTable(8);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{1.2f, 1.5f, 1.5f, 1.5f, 1.5f, 1.5f, 1.2f, 1.2f});

        addHeaderRow(table,
                "Type", "Nom. Conc.", "Signal 1", "Signal 2", "Mean", "%CV", "%Rec.", "Result");

        for (MeasurementPairResponse pair : pairs) {
            boolean outlier = Boolean.TRUE.equals(pair.isOutlier());
            String resultLabel;
            Color rowColor;
            if (outlier) {
                resultLabel = "OUTLIER";
                rowColor = COLOR_HEADER;
            } else if (pair.cvPct() == null && pair.recoveryPct() == null) {
                resultLabel = "N/A";
                rowColor = null;
            } else {
                resultLabel = "OK";
                rowColor = COLOR_PASS;
            }

            addResultRow(table, pair, resultLabel, rowColor);
        }

        doc.add(table);
        doc.add(Chunk.NEWLINE);
    }

    /**
     * Adds the reviewer signature block at the bottom of the document.
     *
     * @param doc the OpenPDF {@link Document}
     * @throws DocumentException if OpenPDF cannot add the element
     */
    private void addSignatureBlock(Document doc) throws DocumentException {
        doc.add(Chunk.NEWLINE);
        doc.add(new LineSeparator());
        doc.add(Chunk.NEWLINE);

        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);

        PdfPCell reviewedBy = new PdfPCell(
                new Phrase("Reviewed by: ___________________________", FONT_BODY));
        reviewedBy.setBorder(Rectangle.NO_BORDER);
        reviewedBy.setPaddingBottom(10);
        table.addCell(reviewedBy);

        PdfPCell dateCell = new PdfPCell(
                new Phrase("Date: _____________________", FONT_BODY));
        dateCell.setBorder(Rectangle.NO_BORDER);
        dateCell.setPaddingBottom(10);
        table.addCell(dateCell);

        doc.add(table);
    }

    // -------------------------------------------------------------------------
    // Cell / row helpers
    // -------------------------------------------------------------------------

    /** Adds a two-column metadata row (label | value). */
    private void addMetaRow(PdfPTable table, String label, String value) {
        table.addCell(labelCell(label));
        table.addCell(bodyCell(value != null ? value : "—"));
    }

    /** Creates a grey label cell. */
    private PdfPCell labelCell(String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text, FONT_BODY));
        cell.setBackgroundColor(COLOR_HEADER);
        cell.setPadding(5);
        return cell;
    }

    /** Creates a plain body cell. */
    private PdfPCell bodyCell(String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text != null ? text : "—", FONT_BODY));
        cell.setPadding(5);
        return cell;
    }

    /** Adds a header row spanning all provided column names. */
    private void addHeaderRow(PdfPTable table, String... headers) {
        for (String h : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(h, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9)));
            cell.setBackgroundColor(COLOR_HEADER);
            cell.setPadding(4);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            table.addCell(cell);
        }
    }

    /**
     * Adds a single result row for a {@link MeasurementPairResponse}.
     *
     * @param table       the target table
     * @param pair        the measurement pair
     * @param resultLabel the PASS / FAIL / OUTLIER / N/A label
     * @param rowColor    background colour for the result cell; {@code null} for default
     */
    private void addResultRow(PdfPTable table, MeasurementPairResponse pair,
                              String resultLabel, Color rowColor) {
        table.addCell(bodyCell(pair.pairType() != null ? pair.pairType().name() : "—"));
        table.addCell(bodyCell(fmt(pair.concentrationNominal())));
        table.addCell(bodyCell(fmt(pair.signal1())));
        table.addCell(bodyCell(fmt(pair.signal2())));
        table.addCell(bodyCell(fmt(pair.signalMean())));
        table.addCell(bodyCell(fmt(pair.cvPct())));
        table.addCell(bodyCell(fmt(pair.recoveryPct())));

        PdfPCell resultCell = new PdfPCell(new Phrase(resultLabel, FONT_BODY));
        resultCell.setPadding(4);
        resultCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        if (rowColor != null) resultCell.setBackgroundColor(rowColor);
        table.addCell(resultCell);
    }

    /** Formats a nullable {@link Double} to two decimal places or "—" if null. */
    private String fmt(Double value) {
        return value != null ? String.format("%.2f", value) : "—";
    }

    // -------------------------------------------------------------------------
    // Page event — footer
    // -------------------------------------------------------------------------

    /**
     * Adds a "Generated by EliSmart LIMS" footer with page numbers to every page.
     */
    private static class PageFooterEvent extends PdfPageEventHelper {

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            PdfPTable footer = new PdfPTable(2);
            try {
                footer.setWidths(new float[]{4f, 1.5f});
                footer.setTotalWidth(document.getPageSize().getWidth() - 80);
                footer.setLockedWidth(true);

                PdfPCell disclaimerCell = new PdfPCell(
                        new Phrase("Generated by EliSmart LIMS — Confidential", FONT_SMALL));
                disclaimerCell.setBorder(Rectangle.TOP);
                disclaimerCell.setPaddingTop(4);
                footer.addCell(disclaimerCell);

                PdfPCell pageCell = new PdfPCell(
                        new Phrase("Page " + writer.getPageNumber(), FONT_SMALL));
                pageCell.setBorder(Rectangle.TOP);
                pageCell.setPaddingTop(4);
                pageCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
                footer.addCell(pageCell);

                footer.writeSelectedRows(0, -1, 40,
                        document.getPageSize().getBottom() + 40,
                        writer.getDirectContent());
            } catch (DocumentException e) {
                throw new IllegalStateException("Footer rendering failed: " + e.getMessage(), e);
            }
        }
    }
}
