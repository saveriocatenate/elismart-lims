package it.elismart_lims.controller;

import it.elismart_lims.service.io.PdfReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints for exporting experiment data to downloadable file formats.
 *
 * <p>Currently supports:
 * <ul>
 *   <li>PDF Certificate of Analysis (CoA) — {@code GET /api/export/experiments/{id}/pdf}</li>
 * </ul>
 *
 * <p>All endpoints require an authenticated user (any role). The actual generation logic
 * lives in {@link PdfReportService} and will be extended by
 * {@code ExcelExportService} in a later roadmap phase.</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/export")
@RequiredArgsConstructor
public class ExportController {

    private final PdfReportService pdfReportService;

    /**
     * Generates and streams a PDF Certificate of Analysis for the specified experiment.
     *
     * <p>The response includes:
     * <ul>
     *   <li>{@code Content-Type: application/pdf}</li>
     *   <li>{@code Content-Disposition: attachment; filename="CoA_experiment_{id}.pdf"}</li>
     * </ul>
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

        return ResponseEntity.ok()
                .headers(headers)
                .body(pdf);
    }
}
