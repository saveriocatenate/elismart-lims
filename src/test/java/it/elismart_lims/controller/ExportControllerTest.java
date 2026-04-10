package it.elismart_lims.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.elismart_lims.config.TestSecurityConfig;
import it.elismart_lims.exception.model.ResourceNotFoundException;
import it.elismart_lims.service.io.ExcelExportService;
import it.elismart_lims.service.io.PdfReportService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller tests for {@link ExportController}.
 *
 * <p>Uses {@link TestSecurityConfig} to bypass JWT authentication.
 * {@link PdfReportService} and {@link ExcelExportService} are mocked.</p>
 */
@Import(TestSecurityConfig.class)
@WebMvcTest(ExportController.class)
class ExportControllerTest {

    private static final MediaType XLSX_MEDIA_TYPE =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private PdfReportService pdfReportService;

    @MockitoBean
    private ExcelExportService excelExportService;

    // -------------------------------------------------------------------------
    // PDF — happy path
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
    // PDF — error cases
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

    // -------------------------------------------------------------------------
    // XLSX single — happy path
    // -------------------------------------------------------------------------

    /**
     * A valid experiment ID returns HTTP 200 with the XLSX MIME type and a non-empty body.
     */
    @Test
    @DisplayName("GET /api/export/experiments/{id}/xlsx — returns XLSX for valid experiment")
    void downloadXlsx_validId_returnsXlsx() throws Exception {
        byte[] fakeXlsx = "PK fake xlsx content".getBytes();
        when(excelExportService.exportExperiment(1L)).thenReturn(fakeXlsx);

        mockMvc.perform(get("/api/export/experiments/1/xlsx"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(XLSX_MEDIA_TYPE))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"experiment_1.xlsx\""))
                .andExpect(content().bytes(fakeXlsx));
    }

    // -------------------------------------------------------------------------
    // XLSX single — error cases
    // -------------------------------------------------------------------------

    /**
     * When the service throws {@link ResourceNotFoundException} for the XLSX endpoint,
     * the global handler must return HTTP 404.
     */
    @Test
    @DisplayName("GET /api/export/experiments/{id}/xlsx — returns 404 for non-existent experiment")
    void downloadXlsx_nonExistentId_returns404() throws Exception {
        when(excelExportService.exportExperiment(99L))
                .thenThrow(new ResourceNotFoundException("Experiment not found with id: 99"));

        mockMvc.perform(get("/api/export/experiments/99/xlsx"))
                .andExpect(status().isNotFound());
    }

    // -------------------------------------------------------------------------
    // XLSX batch — happy path
    // -------------------------------------------------------------------------

    /**
     * A POST with a valid list of IDs returns HTTP 200 with the XLSX MIME type.
     */
    @Test
    @DisplayName("POST /api/export/experiments/xlsx — returns XLSX for valid batch")
    void downloadBatchXlsx_validIds_returnsXlsx() throws Exception {
        byte[] fakeXlsx = "PK fake batch xlsx".getBytes();
        List<Long> ids = List.of(1L, 2L);
        when(excelExportService.exportBatch(ids)).thenReturn(fakeXlsx);

        mockMvc.perform(post("/api/export/experiments/xlsx")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ids)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(XLSX_MEDIA_TYPE))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"experiments_batch.xlsx\""))
                .andExpect(content().bytes(fakeXlsx));
    }

    /**
     * When the batch service throws {@link ResourceNotFoundException} (e.g., one ID is invalid),
     * the global handler must return HTTP 404.
     */
    @Test
    @DisplayName("POST /api/export/experiments/xlsx — returns 404 when any experiment not found")
    void downloadBatchXlsx_unknownId_returns404() throws Exception {
        List<Long> ids = List.of(1L, 99L);
        when(excelExportService.exportBatch(ids))
                .thenThrow(new ResourceNotFoundException("Experiment not found with id: 99"));

        mockMvc.perform(post("/api/export/experiments/xlsx")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(ids)))
                .andExpect(status().isNotFound());
    }
}
