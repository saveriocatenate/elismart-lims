package it.elismart_lims.controller;

import it.elismart_lims.config.TestSecurityConfig;
import it.elismart_lims.exception.model.ResourceNotFoundException;
import it.elismart_lims.service.io.PdfReportService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller tests for {@link ExportController}.
 *
 * <p>Uses {@link TestSecurityConfig} to bypass JWT authentication.
 * {@link PdfReportService} is mocked.</p>
 */
@Import(TestSecurityConfig.class)
@WebMvcTest(ExportController.class)
class ExportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PdfReportService pdfReportService;

    // -------------------------------------------------------------------------
    // Happy path
    // -------------------------------------------------------------------------

    /**
     * A valid experiment ID returns HTTP 200 with Content-Type {@code application/pdf}
     * and a non-empty body.
     */
    @Test
    @DisplayName("GET /api/export/experiments/{id}/pdf — returns PDF for valid experiment")
    void downloadPdf_validId_returnsPdf() throws Exception {
        byte[] fakePdf = "%PDF-1.4 fake content".getBytes();
        when(pdfReportService.generateCertificateOfAnalysis(1L)).thenReturn(fakePdf);

        mockMvc.perform(get("/api/export/experiments/1/pdf"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"CoA_experiment_1.pdf\""))
                .andExpect(content().bytes(fakePdf));
    }

    // -------------------------------------------------------------------------
    // Error cases
    // -------------------------------------------------------------------------

    /**
     * When the service throws {@link ResourceNotFoundException}, the global exception
     * handler must return HTTP 404.
     */
    @Test
    @DisplayName("GET /api/export/experiments/{id}/pdf — returns 404 for non-existent experiment")
    void downloadPdf_nonExistentId_returns404() throws Exception {
        when(pdfReportService.generateCertificateOfAnalysis(99L))
                .thenThrow(new ResourceNotFoundException("Experiment not found with id: 99"));

        mockMvc.perform(get("/api/export/experiments/99/pdf"))
                .andExpect(status().isNotFound());
    }
}
