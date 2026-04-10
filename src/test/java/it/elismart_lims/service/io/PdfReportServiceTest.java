package it.elismart_lims.service.io;

import it.elismart_lims.dto.ExperimentResponse;
import it.elismart_lims.dto.MeasurementPairResponse;
import it.elismart_lims.dto.UsedReagentBatchResponse;
import it.elismart_lims.exception.model.ResourceNotFoundException;
import it.elismart_lims.model.CurveType;
import it.elismart_lims.model.ExperimentStatus;
import it.elismart_lims.model.PairType;
import it.elismart_lims.service.ExperimentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PdfReportService}.
 *
 * <p>No Spring context is loaded. The {@link ExperimentService} dependency is mocked.</p>
 */
@ExtendWith(MockitoExtension.class)
class PdfReportServiceTest {

    @Mock
    private ExperimentService experimentService;

    @InjectMocks
    private PdfReportService pdfReportService;

    private ExperimentResponse validExperiment;

    @BeforeEach
    void setUp() {
        validExperiment = ExperimentResponse.builder()
                .id(1L)
                .name("IgG Run 2026-04-10")
                .date(LocalDateTime.of(2026, 4, 10, 9, 30))
                .status(ExperimentStatus.OK)
                .protocolName("ELISA IgG Test")
                .protocolCurveType(CurveType.FOUR_PARAMETER_LOGISTIC)
                .curveParameters("{\"A\":0.05,\"B\":1.2,\"C\":0.8,\"D\":2.5}")
                .createdBy("analyst1")
                .usedReagentBatches(List.of(
                        UsedReagentBatchResponse.builder()
                                .id(10L)
                                .reagentName("Anti-IgG HRP")
                                .lotNumber("LOT-2026-001")
                                .expiryDate(LocalDate.of(2027, 6, 30))
                                .build()
                ))
                .measurementPairs(List.of(
                        MeasurementPairResponse.builder()
                                .id(100L)
                                .pairType(PairType.CALIBRATION)
                                .concentrationNominal(1.0)
                                .signal1(0.45)
                                .signal2(0.47)
                                .signalMean(0.46)
                                .cvPct(3.07)
                                .recoveryPct(null)
                                .isOutlier(false)
                                .build(),
                        MeasurementPairResponse.builder()
                                .id(101L)
                                .pairType(PairType.CONTROL)
                                .concentrationNominal(0.5)
                                .signal1(0.23)
                                .signal2(0.25)
                                .signalMean(0.24)
                                .cvPct(5.89)
                                .recoveryPct(98.0)
                                .isOutlier(false)
                                .build(),
                        MeasurementPairResponse.builder()
                                .id(102L)
                                .pairType(PairType.SAMPLE)
                                .concentrationNominal(null)
                                .signal1(0.60)
                                .signal2(0.95)
                                .signalMean(0.775)
                                .cvPct(31.9)
                                .recoveryPct(null)
                                .isOutlier(true)
                                .build()
                ))
                .build();
    }

    // -------------------------------------------------------------------------
    // Happy path
    // -------------------------------------------------------------------------

    /**
     * Generating a CoA for a valid experiment must return a non-empty byte array
     * whose first four bytes match the PDF magic number {@code %PDF}.
     */
    @Test
    @DisplayName("generateCertificateOfAnalysis — valid experiment returns non-empty PDF bytes")
    void generateCertificateOfAnalysis_validExperiment_returnsPdfBytes() {
        when(experimentService.getById(1L)).thenReturn(validExperiment);

        byte[] result = pdfReportService.generateCertificateOfAnalysis(1L);

        assertThat(result).isNotNull().isNotEmpty();
        // PDF magic number
        assertThat(new String(result, 0, 4)).isEqualTo("%PDF");
    }

    /**
     * The generated PDF must contain more than 100 bytes — a meaningful document.
     */
    @Test
    @DisplayName("generateCertificateOfAnalysis — generated PDF has meaningful size")
    void generateCertificateOfAnalysis_returnsSufficientlyLargeDocument() {
        when(experimentService.getById(1L)).thenReturn(validExperiment);

        byte[] result = pdfReportService.generateCertificateOfAnalysis(1L);

        assertThat(result.length).isGreaterThan(1000);
    }

    /**
     * An experiment without curve parameters still produces a valid PDF
     * (the section shows a placeholder message instead).
     */
    @Test
    @DisplayName("generateCertificateOfAnalysis — experiment without curveParameters still generates PDF")
    void generateCertificateOfAnalysis_noCurveParameters_stillGeneratesPdf() {
        ExperimentResponse noCurve = ExperimentResponse.builder()
                .id(2L)
                .name("No Curve Run")
                .date(LocalDateTime.of(2026, 4, 10, 10, 0))
                .status(ExperimentStatus.PENDING)
                .protocolName("Bare Protocol")
                .protocolCurveType(CurveType.LINEAR)
                .curveParameters(null)
                .createdBy("analyst2")
                .usedReagentBatches(List.of())
                .measurementPairs(List.of())
                .build();

        when(experimentService.getById(2L)).thenReturn(noCurve);

        byte[] result = pdfReportService.generateCertificateOfAnalysis(2L);

        assertThat(result).isNotEmpty();
        assertThat(new String(result, 0, 4)).isEqualTo("%PDF");
    }

    /**
     * An experiment with KO status must still produce a valid PDF.
     */
    @Test
    @DisplayName("generateCertificateOfAnalysis — KO experiment produces PDF")
    void generateCertificateOfAnalysis_koExperiment_producesPdf() {
        ExperimentResponse ko = ExperimentResponse.builder()
                .id(3L)
                .name("Failed Run")
                .date(LocalDateTime.now())
                .status(ExperimentStatus.KO)
                .protocolName("IgG Protocol")
                .protocolCurveType(CurveType.FOUR_PARAMETER_LOGISTIC)
                .curveParameters(null)
                .createdBy("analyst1")
                .usedReagentBatches(List.of())
                .measurementPairs(List.of())
                .build();

        when(experimentService.getById(3L)).thenReturn(ko);

        byte[] result = pdfReportService.generateCertificateOfAnalysis(3L);

        assertThat(result).isNotEmpty();
        assertThat(new String(result, 0, 4)).isEqualTo("%PDF");
    }

    // -------------------------------------------------------------------------
    // Error cases
    // -------------------------------------------------------------------------

    /**
     * When the experiment does not exist, {@link ExperimentService#getById} throws
     * {@link ResourceNotFoundException}, which must propagate from the service.
     */
    @Test
    @DisplayName("generateCertificateOfAnalysis — non-existent experiment propagates ResourceNotFoundException")
    void generateCertificateOfAnalysis_nonExistentExperiment_throwsResourceNotFoundException() {
        when(experimentService.getById(99L))
                .thenThrow(new ResourceNotFoundException("Experiment not found with id: 99"));

        assertThatThrownBy(() -> pdfReportService.generateCertificateOfAnalysis(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }
}
