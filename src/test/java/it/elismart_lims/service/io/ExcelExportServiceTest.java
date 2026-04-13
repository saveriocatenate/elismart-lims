package it.elismart_lims.service.io;

import it.elismart_lims.dto.ExperimentResponse;
import it.elismart_lims.dto.MeasurementPairResponse;
import it.elismart_lims.dto.ProtocolResponse;
import it.elismart_lims.dto.UsedReagentBatchResponse;
import it.elismart_lims.model.CurveType;
import it.elismart_lims.model.ExperimentStatus;
import it.elismart_lims.model.PairType;
import it.elismart_lims.service.ExperimentService;
import it.elismart_lims.service.ProtocolService;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ExcelExportService}.
 *
 * <p>No Spring context is loaded. {@link ExperimentService} and {@link ProtocolService}
 * are mocked with Mockito.</p>
 */
@ExtendWith(MockitoExtension.class)
class ExcelExportServiceTest {

    @Mock
    private ExperimentService experimentService;

    @Mock
    private ProtocolService protocolService;

    @InjectMocks
    private ExcelExportService excelExportService;

    private ExperimentResponse experiment;
    private ProtocolResponse protocol;

    /**
     * Sets up a minimal but complete experiment + protocol fixture.
     */
    @BeforeEach
    void setUp() {
        MeasurementPairResponse calibrator = MeasurementPairResponse.builder()
                .id(1L)
                .pairType(PairType.CALIBRATION)
                .signal1(1000.0)
                .signal2(1020.0)
                .signalMean(1010.0)
                .cvPct(1.4)
                .concentrationNominal(10.0)
                .recoveryPct(101.0)
                .isOutlier(false)
                .build();

        MeasurementPairResponse control = MeasurementPairResponse.builder()
                .id(2L)
                .pairType(PairType.CONTROL)
                .signal1(500.0)
                .signal2(490.0)
                .signalMean(495.0)
                .cvPct(1.4)
                .concentrationNominal(5.0)
                .recoveryPct(99.0)
                .isOutlier(false)
                .build();

        // A pair whose %CV exceeds the protocol limit — used to verify red-fill logic
        MeasurementPairResponse failPair = MeasurementPairResponse.builder()
                .id(3L)
                .pairType(PairType.CONTROL)
                .signal1(500.0)
                .signal2(700.0)
                .signalMean(600.0)
                .cvPct(23.6)
                .concentrationNominal(5.0)
                .recoveryPct(120.0)
                .isOutlier(false)
                .build();

        UsedReagentBatchResponse batch = UsedReagentBatchResponse.builder()
                .id(1L)
                .reagentBatch(it.elismart_lims.dto.ReagentBatchResponse.builder()
                        .id(5L).reagentId(1L).reagentName("Anti-IgG antibody").manufacturer("Sigma")
                        .lotNumber("LOT-2024-001").expiryDate(LocalDate.of(2026, 12, 31)).build())
                .build();

        experiment = ExperimentResponse.builder()
                .id(1L)
                .name("Test Experiment Alpha")
                .date(LocalDateTime.of(2025, 3, 15, 10, 0))
                .status(ExperimentStatus.OK)
                .protocolName("Protocol-A")
                .protocolCurveType(CurveType.FOUR_PARAMETER_LOGISTIC)
                .curveParameters("{\"A\":0.1,\"B\":1.5,\"C\":2.0,\"D\":3.5}")
                .createdBy("analyst1")
                .usedReagentBatches(List.of(batch))
                .measurementPairs(List.of(calibrator, control, failPair))
                .build();

        protocol = ProtocolResponse.builder()
                .id(1L)
                .name("Protocol-A")
                .maxCvAllowed(15.0)
                .maxErrorAllowed(20.0)
                .curveType(CurveType.FOUR_PARAMETER_LOGISTIC)
                .build();
    }

    // -------------------------------------------------------------------------
    // exportExperiment
    // -------------------------------------------------------------------------

    /**
     * A valid experiment ID must produce a non-empty XLSX file with exactly 3 sheets:
     * {@code Summary}, {@code Raw Data}, and {@code Reagent Batches}.
     */
    @Test
    @DisplayName("exportExperiment — returns valid XLSX with 3 sheets")
    void exportExperiment_validId_returnsValidXlsx() throws Exception {
        when(experimentService.getById(1L)).thenReturn(experiment);
        when(protocolService.getByName("Protocol-A")).thenReturn(protocol);

        byte[] xlsx = excelExportService.exportExperiment(1L);

        assertThat(xlsx).isNotEmpty();

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(3);
            assertThat(workbook.getSheetName(0)).isEqualTo("Summary");
            assertThat(workbook.getSheetName(1)).isEqualTo("Raw Data");
            assertThat(workbook.getSheetName(2)).isEqualTo("Reagent Batches");
        }
    }

    /**
     * The {@code Raw Data} sheet header row must contain all expected column names.
     */
    @Test
    @DisplayName("exportExperiment — Raw Data sheet has correct column headers")
    void exportExperiment_rawDataSheet_hasCorrectHeaders() throws Exception {
        when(experimentService.getById(1L)).thenReturn(experiment);
        when(protocolService.getByName("Protocol-A")).thenReturn(protocol);

        byte[] xlsx = excelExportService.exportExperiment(1L);

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            Sheet rawData = workbook.getSheet("Raw Data");
            assertThat(rawData).isNotNull();

            var header = rawData.getRow(0);
            assertThat(header.getCell(0).getStringCellValue()).isEqualTo("Type");
            assertThat(header.getCell(1).getStringCellValue()).isEqualTo("Signal 1");
            assertThat(header.getCell(2).getStringCellValue()).isEqualTo("Signal 2");
            assertThat(header.getCell(3).getStringCellValue()).isEqualTo("Mean");
            assertThat(header.getCell(4).getStringCellValue()).isEqualTo("%CV");
            assertThat(header.getCell(5).getStringCellValue()).isEqualTo("Nom. Conc.");
            assertThat(header.getCell(6).getStringCellValue()).isEqualTo("%Recovery");
            assertThat(header.getCell(7).getStringCellValue()).isEqualTo("Outlier");
        }
    }

    /**
     * The {@code Raw Data} sheet must contain one data row per measurement pair
     * (3 pairs in the fixture).
     */
    @Test
    @DisplayName("exportExperiment — Raw Data sheet has one row per MeasurementPair")
    void exportExperiment_rawDataSheet_hasCorrectRowCount() throws Exception {
        when(experimentService.getById(1L)).thenReturn(experiment);
        when(protocolService.getByName("Protocol-A")).thenReturn(protocol);

        byte[] xlsx = excelExportService.exportExperiment(1L);

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            Sheet rawData = workbook.getSheet("Raw Data");
            // Row 0 is the header; data starts at row 1
            assertThat(rawData.getLastRowNum()).isEqualTo(3); // header + 3 pairs
        }
    }

    /**
     * The {@code Summary} sheet must contain the experiment name in a value cell.
     */
    @Test
    @DisplayName("exportExperiment — Summary sheet contains experiment name")
    void exportExperiment_summarySheet_containsExperimentName() throws Exception {
        when(experimentService.getById(1L)).thenReturn(experiment);
        when(protocolService.getByName("Protocol-A")).thenReturn(protocol);

        byte[] xlsx = excelExportService.exportExperiment(1L);

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            Sheet summary = workbook.getSheet("Summary");
            assertThat(summary).isNotNull();

            // Row 0 is the "Experiment Metadata" section header; row 1 is "Name" / value
            String nameValue = summary.getRow(1).getCell(1).getStringCellValue();
            assertThat(nameValue).isEqualTo("Test Experiment Alpha");
        }
    }

    /**
     * The {@code Reagent Batches} sheet must contain one data row (the fixture has 1 batch)
     * plus the header row.
     */
    @Test
    @DisplayName("exportExperiment — Reagent Batches sheet has correct row count")
    void exportExperiment_reagentBatchesSheet_hasCorrectRowCount() throws Exception {
        when(experimentService.getById(1L)).thenReturn(experiment);
        when(protocolService.getByName("Protocol-A")).thenReturn(protocol);

        byte[] xlsx = excelExportService.exportExperiment(1L);

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            Sheet batches = workbook.getSheet("Reagent Batches");
            assertThat(batches).isNotNull();
            // header + 1 data row
            assertThat(batches.getLastRowNum()).isEqualTo(1);
        }
    }

    // -------------------------------------------------------------------------
    // exportBatch
    // -------------------------------------------------------------------------

    /**
     * A batch export with two experiments must produce a valid XLSX file with
     * two Summary sheets (one per experiment) and one consolidated Raw Data sheet.
     */
    @Test
    @DisplayName("exportBatch — returns valid XLSX with Summary sheets + consolidated Raw Data")
    void exportBatch_twoExperiments_returnsValidXlsx() throws Exception {
        ExperimentResponse experiment2 = ExperimentResponse.builder()
                .id(2L)
                .name("Test Experiment Beta")
                .date(LocalDateTime.of(2025, 4, 1, 9, 30))
                .status(ExperimentStatus.PENDING)
                .protocolName("Protocol-A")
                .protocolCurveType(CurveType.FOUR_PARAMETER_LOGISTIC)
                .curveParameters(null)
                .createdBy("analyst2")
                .usedReagentBatches(List.of())
                .measurementPairs(List.of(
                        MeasurementPairResponse.builder()
                                .id(4L)
                                .pairType(PairType.CALIBRATION)
                                .signal1(800.0).signal2(820.0)
                                .signalMean(810.0).cvPct(1.7)
                                .concentrationNominal(8.0).recoveryPct(101.25)
                                .isOutlier(false)
                                .build()
                ))
                .build();

        when(experimentService.getById(1L)).thenReturn(experiment);
        when(experimentService.getById(2L)).thenReturn(experiment2);
        when(protocolService.getByName("Protocol-A")).thenReturn(protocol);

        byte[] xlsx = excelExportService.exportBatch(List.of(1L, 2L));

        assertThat(xlsx).isNotEmpty();

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            // 2 Summary sheets + 1 Raw Data sheet = 3 total
            assertThat(workbook.getNumberOfSheets()).isEqualTo(3);

            // Both Summary sheets must be present (names truncated at 31 chars)
            assertThat(workbook.getSheetName(0)).startsWith("Summary_");
            assertThat(workbook.getSheetName(1)).startsWith("Summary_");
            assertThat(workbook.getSheetName(2)).isEqualTo("Raw Data");
        }
    }

    /**
     * The consolidated Raw Data sheet must contain rows from all experiments:
     * 3 pairs from experiment 1 + 1 pair from experiment 2 = 4 data rows.
     */
    @Test
    @DisplayName("exportBatch — consolidated Raw Data sheet aggregates pairs from all experiments")
    void exportBatch_consolidatedRawData_containsPairsFromAllExperiments() throws Exception {
        ExperimentResponse experiment2 = ExperimentResponse.builder()
                .id(2L)
                .name("Test Experiment Beta")
                .date(LocalDateTime.of(2025, 4, 1, 9, 30))
                .status(ExperimentStatus.PENDING)
                .protocolName("Protocol-A")
                .protocolCurveType(CurveType.FOUR_PARAMETER_LOGISTIC)
                .curveParameters(null)
                .createdBy("analyst2")
                .usedReagentBatches(List.of())
                .measurementPairs(List.of(
                        MeasurementPairResponse.builder()
                                .id(4L)
                                .pairType(PairType.CALIBRATION)
                                .signal1(800.0).signal2(820.0)
                                .signalMean(810.0).cvPct(1.7)
                                .concentrationNominal(8.0).recoveryPct(101.25)
                                .isOutlier(false)
                                .build()
                ))
                .build();

        when(experimentService.getById(1L)).thenReturn(experiment);
        when(experimentService.getById(2L)).thenReturn(experiment2);
        when(protocolService.getByName("Protocol-A")).thenReturn(protocol);

        byte[] xlsx = excelExportService.exportBatch(List.of(1L, 2L));

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            Sheet rawData = workbook.getSheet("Raw Data");
            assertThat(rawData).isNotNull();

            // Header row (0) + 3 pairs from exp1 + 1 pair from exp2 = lastRowNum 4
            assertThat(rawData.getLastRowNum()).isEqualTo(4);

            // First column of row 1 must contain the first experiment's name
            assertThat(rawData.getRow(1).getCell(0).getStringCellValue())
                    .isEqualTo("Test Experiment Alpha");
        }
    }
}
