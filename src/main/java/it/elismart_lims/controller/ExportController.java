package it.elismart_lims.controller;

import it.elismart_lims.service.io.ExcelExportService;
import it.elismart_lims.service.io.PdfReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST endpoints for exporting experiment data to downloadable file formats.
 *
 * <p>Supported formats:
 * <ul>
 *   <li>PDF Certificate of Analysis — {@code GET /api/export/experiments/{id}/pdf}</li>
 *   <li>Excel (XLSX) single experiment — {@code GET /api/export/experiments/{id}/xlsx}</li>
 *   <li>Excel (XLSX) batch — {@code POST /api/export/experiments/xlsx} with a JSON array of IDs</li>
 * </ul>
 *
 * <p>All endpoints require an authenticated user (any role).
 */
@Slf4j
@RestController
@RequestMapping("/api/export")
@RequiredArgsConstructor
public class ExportController {

    /** MIME type for OOXML workbooks. */
    private static final MediaType XLSX_MEDIA_TYPE =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final PdfReportService pdfReportService;
    private final ExcelExportService excelExportService;

    // -------------------------------------------------------------------------
    // PDF
    // -------------------------------------------------------------------------

    /**
     * Generates and streams a PDF Certificate of Analysis for the specified experiment.
     *
     * @param id the experiment ID
     * @return the raw PDF bytes as a downloadable attachment
     */
    @GetMapping("/experiments/{id}/pdf")
    public ResponseEntity<byte[]> downloadPdf(@PathVariable Long id) {
        log.info("PDF export requested for experiment id={}", id);

        byte[] pdf = pdfReportService.generateCertificateOfAnalysis(id);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename("CoA_experiment_" + id + ".pdf")
                .build());
        headers.setContentLength(pdf.length);

        return ResponseEntity.ok().headers(headers).body(pdf);
    }

    // -------------------------------------------------------------------------
    // Excel — single experiment
    // -------------------------------------------------------------------------

    /**
     * Generates and streams an XLSX export for a single experiment.
     *
     * <p>The workbook contains three sheets: {@code Summary}, {@code Raw Data},
     * and {@code Reagent Batches}.
     *
     * @param id the experiment ID
     * @return the raw XLSX bytes as a downloadable attachment
     */
    @GetMapping("/experiments/{id}/xlsx")
    public ResponseEntity<byte[]> downloadXlsx(@PathVariable Long id) {
        log.info("XLSX export requested for experiment id={}", id);

        byte[] xlsx = excelExportService.exportExperiment(id);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(XLSX_MEDIA_TYPE);
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename("experiment_" + id + ".xlsx")
                .build());
        headers.setContentLength(xlsx.length);

        return ResponseEntity.ok().headers(headers).body(xlsx);
    }

    // -------------------------------------------------------------------------
    // Excel — batch
    // -------------------------------------------------------------------------

    /**
     * Generates and streams a batch XLSX export for multiple experiments.
     *
     * <p>The workbook contains one {@code Summary_<name>} sheet per experiment and
     * a single consolidated {@code Raw Data} sheet.
     *
     * @param ids the list of experiment IDs to include
     * @return the raw XLSX bytes as a downloadable attachment
     */
    @PostMapping("/experiments/xlsx")
    public ResponseEntity<byte[]> downloadBatchXlsx(@RequestBody List<Long> ids) {
        log.info("Batch XLSX export requested for {} experiments", ids.size());

        byte[] xlsx = excelExportService.exportBatch(ids);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(XLSX_MEDIA_TYPE);
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename("experiments_batch.xlsx")
                .build());
        headers.setContentLength(xlsx.length);

        return ResponseEntity.ok().headers(headers).body(xlsx);
    }
}
