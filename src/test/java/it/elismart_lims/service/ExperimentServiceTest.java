package it.elismart_lims.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.elismart_lims.dto.ExperimentPage;
import it.elismart_lims.dto.ExperimentRequest;
import it.elismart_lims.dto.ExperimentResponse;
import it.elismart_lims.dto.ExperimentSearchRequest;
import it.elismart_lims.dto.ExperimentUpdateRequest;
import it.elismart_lims.dto.MeasurementPairRequest;
import it.elismart_lims.dto.UsedReagentBatchRequest;
import it.elismart_lims.dto.UsedReagentBatchUpdateRequest;
import it.elismart_lims.exception.model.ProtocolMismatchException;
import it.elismart_lims.exception.model.ResourceNotFoundException;
import it.elismart_lims.model.CurveType;
import it.elismart_lims.model.Experiment;
import it.elismart_lims.model.ExperimentStatus;
import it.elismart_lims.model.MeasurementPair;
import it.elismart_lims.model.PairType;
import it.elismart_lims.model.Protocol;
import it.elismart_lims.model.ReagentBatch;
import it.elismart_lims.model.ReagentCatalog;
import it.elismart_lims.model.UsedReagentBatch;
import it.elismart_lims.repository.ExperimentRepository;
import it.elismart_lims.dto.CsvFormat;
import it.elismart_lims.dto.CsvImportConfig;
import it.elismart_lims.dto.WellMapping;
import it.elismart_lims.service.audit.AuditLogService;
import it.elismart_lims.service.curve.CurveFittingService;
import it.elismart_lims.service.curve.CurveParameters;
import it.elismart_lims.service.io.CsvImportService;
import it.elismart_lims.service.validation.OutlierDetectionService;
import it.elismart_lims.service.validation.ValidationEngine;
import it.elismart_lims.service.validation.ValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ExperimentService}.
 */
@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class ExperimentServiceTest {

    @Mock
    private ExperimentRepository experimentRepository;

    @Mock
    private ProtocolService protocolService;

    @Mock
    private UsedReagentBatchService usedReagentBatchService;

    @Mock
    private ReagentBatchService reagentBatchService;

    @Mock
    private MeasurementPairService measurementPairService;

    @Mock
    private ProtocolReagentSpecService protocolReagentSpecService;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private CurveFittingService curveFittingService;

    @Mock
    private OutlierDetectionService outlierDetectionService;

    @Mock
    private ValidationEngine validationEngine;

    @Mock
    private CsvImportService csvImportService;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private ExperimentService experimentService;

    private Protocol protocol;
    private UsedReagentBatch batch;
    private Experiment experiment;
    private ExperimentRequest request;

    @BeforeEach
    void setUp() {
        ReagentCatalog reagent = ReagentCatalog.builder()
                .id(1L)
                .name("Anti-IgG")
                .manufacturer("Sigma")
                .build();

        protocol = Protocol.builder()
                .id(10L)
                .name("ELISA Test")
                .numCalibrationPairs(7)
                .numControlPairs(3)
                .maxCvAllowed(15.0)
                .maxErrorAllowed(10.0)
                .curveType(CurveType.LINEAR)
                .build();

        ReagentBatch reagentBatch = ReagentBatch.builder()
                .id(50L)
                .reagent(reagent)
                .lotNumber("LOT-001")
                .expiryDate(LocalDate.of(2027, 12, 31))
                .build();

        batch = UsedReagentBatch.builder()
                .id(100L)
                .experiment(null)
                .reagent(reagent)
                .reagentBatch(reagentBatch)
                .build();

        experiment = Experiment.builder()
                .id(1L)
                .name("Test Experiment")
                .date(LocalDateTime.of(2026, 4, 5, 10, 0))
                .status(ExperimentStatus.COMPLETED)
                .protocol(protocol)
                .usedReagentBatches(List.of(batch))
                .measurementPairs(List.of())
                .build();

        MeasurementPairRequest pairRequest = new MeasurementPairRequest(
                PairType.CALIBRATION, null, 0.45, 0.47, 98.5, false);

        // batchRequest now carries the reagentBatch id (50L), not a reagentId
        UsedReagentBatchRequest batchRequest = new UsedReagentBatchRequest(50L);

        request = new ExperimentRequest(
                "Test Experiment",
                LocalDateTime.of(2026, 4, 5, 10, 0),
                10L,
                ExperimentStatus.PENDING,
                List.of(batchRequest),
                List.of(pairRequest));
    }

    @Test
    void getById_shouldReturnResponse_whenExists() {
        when(experimentRepository.findById(1L)).thenReturn(Optional.of(experiment));

        ExperimentResponse result = experimentService.getById(1L);

        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.name()).isEqualTo("Test Experiment");
        assertThat(result.status()).isEqualTo(ExperimentStatus.COMPLETED);
        verify(experimentRepository).findById(1L);
    }

    @Test
    void getById_shouldThrow_whenNotFound() {
        when(experimentRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> experimentService.getById(1L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Experiment not found with id: 1");
    }

    @Test
    void create_shouldSaveExperimentAndReturnResponse() {
        // reagentBatchId=50 resolves to reagent.id=1; mandatory set is empty → passes validation
        ReagentBatch rb = ReagentBatch.builder().id(50L)
                .reagent(ReagentCatalog.builder().id(1L).build()).lotNumber("LOT-001").build();
        when(reagentBatchService.getEntityById(50L)).thenReturn(rb);
        when(protocolService.getEntityById(10L)).thenReturn(protocol);
        when(protocolReagentSpecService.getMandatoryReagentIds(10L)).thenReturn(Set.of());
        when(experimentRepository.save(any(Experiment.class))).thenReturn(experiment);
        when(usedReagentBatchService.createAllForExperiment(anyList(), any(Experiment.class)))
                .thenReturn(List.of(batch));
        when(measurementPairService.saveAll(anyList())).thenReturn(List.of());

        ExperimentResponse result = experimentService.create(request);

        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.name()).isEqualTo("Test Experiment");
        assertThat(result.protocolName()).isEqualTo("ELISA Test");
        verify(experimentRepository).save(any(Experiment.class));
        verify(usedReagentBatchService).createAllForExperiment(anyList(), any(Experiment.class));
        verify(measurementPairService).saveAll(anyList());
    }

    @Test
    void create_shouldThrow_whenMandatoryReagentsMissing() {
        // reagentBatchId=50 resolves to reagent.id=1; mandatory set {1,2} → missing 2 → throws
        ReagentBatch rb = ReagentBatch.builder().id(50L)
                .reagent(ReagentCatalog.builder().id(1L).build()).lotNumber("LOT-001").build();
        when(reagentBatchService.getEntityById(50L)).thenReturn(rb);
        when(protocolService.getEntityById(10L)).thenReturn(protocol);
        when(protocolReagentSpecService.getMandatoryReagentIds(10L)).thenReturn(Set.of(1L, 2L));

        assertThatThrownBy(() -> experimentService.create(request))
                .isInstanceOf(ProtocolMismatchException.class)
                .hasMessageContaining("must include all mandatory reagents");
        verify(experimentRepository, never()).save(any());
    }

    @Test
    void delete_shouldRemove_whenExists() {
        when(experimentRepository.existsById(1L)).thenReturn(true);

        experimentService.delete(1L);

        verify(experimentRepository).deleteById(1L);
    }

    @Test
    void delete_shouldThrow_whenNotFound() {
        when(experimentRepository.existsById(1L)).thenReturn(false);

        assertThatThrownBy(() -> experimentService.delete(1L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Experiment not found with id: 1");
        verify(experimentRepository, never()).deleteById(anyLong());
    }

    @Test
    void search_shouldReturnPagedResultsWithFilters() {
        ExperimentSearchRequest searchRequest = new ExperimentSearchRequest(
                "test", null, null, null, ExperimentStatus.COMPLETED, 0, 10);

        when(experimentRepository.findAll(any(Specification.class), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(experiment)));

        ExperimentPage result = experimentService.search(searchRequest);

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().getFirst().name()).isEqualTo("Test Experiment");
        assertThat(result.totalElements()).isEqualTo(1);
        verify(experimentRepository).findAll(any(Specification.class), any(PageRequest.class));
    }

    @Test
    void update_shouldUpdateFieldsAndReturnResponse() {
        UsedReagentBatchUpdateRequest batchUpdate = new UsedReagentBatchUpdateRequest(100L, 6L);
        // Status changes from COMPLETED → PENDING (valid client transition, no reason required)
        ExperimentUpdateRequest updateRequest = new ExperimentUpdateRequest(
                "Updated Name",
                LocalDateTime.of(2026, 5, 1, 9, 0),
                ExperimentStatus.PENDING,
                List.of(batchUpdate),
                null,
                null);

        when(experimentRepository.findById(1L)).thenReturn(Optional.of(experiment));
        when(experimentRepository.save(any(Experiment.class))).thenReturn(experiment);

        ExperimentResponse result = experimentService.update(1L, updateRequest);

        assertThat(result.id()).isEqualTo(1L);
        verify(experimentRepository).findById(1L);
        verify(usedReagentBatchService).updateBatch(batchUpdate, 1L);
        verify(experimentRepository).save(any(Experiment.class));
    }

    @Test
    void existsByProtocolId_shouldReturnTrue_whenExperimentsExist() {
        when(experimentRepository.existsByProtocolId(10L)).thenReturn(true);

        assertThat(experimentService.existsByProtocolId(10L)).isTrue();
        verify(experimentRepository).existsByProtocolId(10L);
    }

    @Test
    void update_shouldThrow_whenExperimentNotFound() {
        ExperimentUpdateRequest updateRequest = new ExperimentUpdateRequest(
                "Name",
                LocalDateTime.of(2026, 5, 1, 9, 0),
                ExperimentStatus.PENDING,
                List.of(),
                null,
                null);

        when(experimentRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> experimentService.update(99L, updateRequest))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Experiment not found with id: 99");
        verify(experimentRepository, never()).save(any());
    }

    @Test
    void search_shouldReturnEmpty_whenNoMatches() {
        ExperimentSearchRequest searchRequest = new ExperimentSearchRequest(
                "nonexistent", null, null, null, null, 0, 10);

        when(experimentRepository.findAll(any(Specification.class), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(Collections.emptyList()));

        ExperimentPage result = experimentService.search(searchRequest);

        assertThat(result.content()).isEmpty();
    }

    @Test
    void update_shouldAuditNameChange_whenNameDiffers() {
        // Status unchanged (COMPLETED → COMPLETED) — no reason required
        ExperimentUpdateRequest updateRequest = new ExperimentUpdateRequest(
                "New Name",
                experiment.getDate(),
                experiment.getStatus(),
                List.of(),
                null,
                null);

        when(experimentRepository.findById(1L)).thenReturn(Optional.of(experiment));
        when(experimentRepository.save(any(Experiment.class))).thenReturn(experiment);

        experimentService.update(1L, updateRequest);

        verify(auditLogService).logChange("Experiment", 1L, "name", "Test Experiment", "New Name", null);
    }

    @Test
    void update_shouldNotAudit_whenNothingChanges() {
        ExperimentUpdateRequest updateRequest = new ExperimentUpdateRequest(
                experiment.getName(),
                experiment.getDate(),
                experiment.getStatus(),
                List.of(),
                null,
                null);

        when(experimentRepository.findById(1L)).thenReturn(Optional.of(experiment));
        when(experimentRepository.save(any(Experiment.class))).thenReturn(experiment);

        experimentService.update(1L, updateRequest);

        verify(auditLogService, never()).logChange(any(), any(), any(), any(), any(), any());
    }

    @Test
    void update_shouldThrow_whenClientTriesToSetEngineOnlyStatus() {
        // COMPLETED → OK is forbidden for client requests — only the validation engine may set OK
        ExperimentUpdateRequest updateRequest = new ExperimentUpdateRequest(
                experiment.getName(),
                experiment.getDate(),
                ExperimentStatus.OK,
                List.of(),
                null,
                null);

        when(experimentRepository.findById(1L)).thenReturn(Optional.of(experiment));

        assertThatThrownBy(() -> experimentService.update(1L, updateRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ERR_INVALID_STATUS_TRANSITION")
                .hasMessageContaining("validation engine");
        verify(experimentRepository, never()).save(any());
    }

    @Test
    void update_shouldThrow_whenStatusChangesFromTerminalWithoutReason() {
        // Start from OK, try to change to PENDING without reason — must throw
        Experiment okExperiment = Experiment.builder()
                .id(2L)
                .name("OK Experiment")
                .date(LocalDateTime.of(2026, 4, 1, 9, 0))
                .status(ExperimentStatus.OK)
                .protocol(protocol)
                .usedReagentBatches(List.of())
                .measurementPairs(List.of())
                .build();

        ExperimentUpdateRequest updateRequest = new ExperimentUpdateRequest(
                okExperiment.getName(),
                okExperiment.getDate(),
                ExperimentStatus.PENDING,
                List.of(),
                null,
                "   ");  // blank reason — equivalent to absent

        when(experimentRepository.findById(2L)).thenReturn(Optional.of(okExperiment));

        assertThatThrownBy(() -> experimentService.update(2L, updateRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ERR_REASON_REQUIRED");
        verify(experimentRepository, never()).save(any());
    }

    // -------------------------------------------------------------------------
    // State-machine transition tests
    // -------------------------------------------------------------------------

    /**
     * PENDING → COMPLETED is a valid client transition.
     */
    @Test
    void update_pendingToCompleted_shouldSucceed() {
        Experiment pendingExp = Experiment.builder()
                .id(3L).name("Pending Exp")
                .date(LocalDateTime.of(2026, 4, 10, 8, 0))
                .status(ExperimentStatus.PENDING)
                .protocol(protocol)
                .usedReagentBatches(List.of())
                .measurementPairs(List.of())
                .build();

        ExperimentUpdateRequest req = new ExperimentUpdateRequest(
                pendingExp.getName(), pendingExp.getDate(),
                ExperimentStatus.COMPLETED, List.of(), null, null);

        when(experimentRepository.findById(3L)).thenReturn(Optional.of(pendingExp));
        when(experimentRepository.save(any(Experiment.class))).thenReturn(pendingExp);

        ExperimentResponse result = experimentService.update(3L, req);

        assertThat(result).isNotNull();
        verify(experimentRepository).save(any(Experiment.class));
    }

    /**
     * PENDING → OK via the update API must be rejected — OK is engine-only.
     */
    @Test
    void update_pendingToOk_shouldBeRejectedWithEngineOnlyError() {
        Experiment pendingExp = Experiment.builder()
                .id(3L).name("Pending Exp")
                .date(LocalDateTime.of(2026, 4, 10, 8, 0))
                .status(ExperimentStatus.PENDING)
                .protocol(protocol)
                .usedReagentBatches(List.of())
                .measurementPairs(List.of())
                .build();

        ExperimentUpdateRequest req = new ExperimentUpdateRequest(
                pendingExp.getName(), pendingExp.getDate(),
                ExperimentStatus.OK, List.of(), null, "some reason");

        when(experimentRepository.findById(3L)).thenReturn(Optional.of(pendingExp));

        assertThatThrownBy(() -> experimentService.update(3L, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ERR_INVALID_STATUS_TRANSITION")
                .hasMessageContaining("validation engine");
        verify(experimentRepository, never()).save(any());
    }

    /**
     * OK → PENDING is a valid client transition (re-analysis) when a reason is provided.
     */
    @Test
    void update_okToPending_withReason_shouldSucceed() {
        Experiment okExp = Experiment.builder()
                .id(4L).name("OK Exp")
                .date(LocalDateTime.of(2026, 4, 10, 8, 0))
                .status(ExperimentStatus.OK)
                .protocol(protocol)
                .usedReagentBatches(List.of())
                .measurementPairs(List.of())
                .build();

        ExperimentUpdateRequest req = new ExperimentUpdateRequest(
                okExp.getName(), okExp.getDate(),
                ExperimentStatus.PENDING, List.of(), null, "Re-analysis requested by reviewer");

        when(experimentRepository.findById(4L)).thenReturn(Optional.of(okExp));
        when(experimentRepository.save(any(Experiment.class))).thenReturn(okExp);

        ExperimentResponse result = experimentService.update(4L, req);

        assertThat(result).isNotNull();
        verify(auditLogService).logChange(eq("Experiment"), eq(4L), eq("status"),
                eq("OK"), eq("PENDING"), eq("Re-analysis requested by reviewer"));
        verify(experimentRepository).save(any(Experiment.class));
    }

    /**
     * KO → OK via the update API must be rejected — OK is engine-only regardless of source.
     */
    @Test
    void update_koToOk_shouldBeRejectedWithEngineOnlyError() {
        Experiment koExp = Experiment.builder()
                .id(5L).name("KO Exp")
                .date(LocalDateTime.of(2026, 4, 10, 8, 0))
                .status(ExperimentStatus.KO)
                .protocol(protocol)
                .usedReagentBatches(List.of())
                .measurementPairs(List.of())
                .build();

        ExperimentUpdateRequest req = new ExperimentUpdateRequest(
                koExp.getName(), koExp.getDate(),
                ExperimentStatus.OK, List.of(), null, "Override approved");

        when(experimentRepository.findById(5L)).thenReturn(Optional.of(koExp));

        assertThatThrownBy(() -> experimentService.update(5L, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ERR_INVALID_STATUS_TRANSITION")
                .hasMessageContaining("validation engine");
        verify(experimentRepository, never()).save(any());
    }

    // -------------------------------------------------------------------------
    // validate() tests
    // -------------------------------------------------------------------------

    /**
     * Validates an experiment with a CALIBRATION pair. Expects the status to update to OK
     * and curveParameters to be written back to the entity.
     */
    @Test
    void validate_shouldReturnOk_whenDatasetValid() throws JsonProcessingException {
        MeasurementPair calPair = MeasurementPair.builder()
                .id(1L)
                .pairType(PairType.CALIBRATION)
                .concentrationNominal(10.0)
                .signal1(3.0)
                .signal2(3.0)
                .signalMean(3.0)
                .cvPct(0.0)
                .isOutlier(false)
                .build();

        Experiment experimentWithPairs = Experiment.builder()
                .id(1L)
                .name("Test Experiment")
                .date(LocalDateTime.of(2026, 4, 5, 10, 0))
                .status(ExperimentStatus.COMPLETED)
                .protocol(protocol)
                .measurementPairs(List.of(calPair))
                .usedReagentBatches(List.of())
                .build();

        CurveParameters params = new CurveParameters(Map.of("slope", 2.0, "intercept", 1.0));
        ValidationResult okResult = new ValidationResult(ExperimentStatus.OK, List.of(), params);

        when(experimentRepository.findById(1L)).thenReturn(Optional.of(experimentWithPairs));
        when(curveFittingService.fitCurve(any(), anyList())).thenReturn(params);
        when(objectMapper.writeValueAsString(params)).thenReturn("{\"values\":{\"slope\":2.0}}");
        when(validationEngine.evaluate(any(), any(), any())).thenReturn(okResult);
        when(experimentRepository.save(any(Experiment.class))).thenReturn(experimentWithPairs);

        ExperimentResponse result = experimentService.validate(1L);

        assertThat(result.id()).isEqualTo(1L);
        verify(curveFittingService).fitCurve(eq(CurveType.LINEAR), anyList());
        verify(validationEngine).evaluate(any(), any(), eq(params));
        verify(experimentRepository).save(any(Experiment.class));
        verify(auditLogService).logChange(eq("Experiment"), eq(1L), eq("status"),
                eq("COMPLETED"), eq("OK"), isNull());
    }

    /**
     * Validates an experiment that fails protocol limits. Expects the status to update to KO.
     */
    @Test
    void validate_shouldReturnKo_whenDatasetInvalid() throws JsonProcessingException {
        MeasurementPair calPair = MeasurementPair.builder()
                .id(1L)
                .pairType(PairType.CALIBRATION)
                .concentrationNominal(10.0)
                .signal1(3.0)
                .signal2(3.0)
                .signalMean(3.0)
                .cvPct(0.0)
                .isOutlier(false)
                .build();

        Experiment experimentWithPairs = Experiment.builder()
                .id(1L)
                .name("Test Experiment")
                .date(LocalDateTime.of(2026, 4, 5, 10, 0))
                .status(ExperimentStatus.COMPLETED)
                .protocol(protocol)
                .measurementPairs(List.of(calPair))
                .usedReagentBatches(List.of())
                .build();

        CurveParameters params = new CurveParameters(Map.of("slope", 2.0, "intercept", 1.0));
        ValidationResult koResult = new ValidationResult(ExperimentStatus.KO, List.of(), params);

        when(experimentRepository.findById(1L)).thenReturn(Optional.of(experimentWithPairs));
        when(curveFittingService.fitCurve(any(), anyList())).thenReturn(params);
        when(objectMapper.writeValueAsString(params)).thenReturn("{\"values\":{\"slope\":2.0}}");
        when(validationEngine.evaluate(any(), any(), any())).thenReturn(koResult);
        when(experimentRepository.save(any(Experiment.class))).thenReturn(experimentWithPairs);

        ExperimentResponse result = experimentService.validate(1L);

        assertThat(result.id()).isEqualTo(1L);
        verify(auditLogService).logChange(eq("Experiment"), eq(1L), eq("status"),
                eq("COMPLETED"), eq("KO"), isNull());
    }

    /**
     * Validates that a 404 is thrown for an unknown experiment ID.
     */
    @Test
    void validate_shouldThrow_whenExperimentNotFound() {
        when(experimentRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> experimentService.validate(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Experiment not found with id: 99");
        verify(curveFittingService, never()).fitCurve(any(), anyList());
    }

    /**
     * When {@link OutlierDetectionService} flags a pair, validate() must set
     * {@code isOutlier=true} on that pair and emit an audit log entry before
     * delegating to {@link ValidationEngine}.
     */
    @Test
    void validate_shouldAutoFlagOutliers_andAuditEachFlag() throws JsonProcessingException {
        MeasurementPair calPair = MeasurementPair.builder()
                .id(1L)
                .pairType(PairType.CALIBRATION)
                .concentrationNominal(10.0)
                .signal1(3.0).signal2(3.0)
                .signalMean(3.0).cvPct(0.0)
                .isOutlier(false)
                .build();

        MeasurementPair badPair = MeasurementPair.builder()
                .id(2L)
                .pairType(PairType.CONTROL)
                .concentrationNominal(5.0)
                .signal1(1.0).signal2(20.0)
                .signalMean(10.5).cvPct(135.0)
                .isOutlier(false)
                .build();

        Experiment experimentWithPairs = Experiment.builder()
                .id(1L)
                .name("Test Experiment")
                .date(LocalDateTime.of(2026, 4, 5, 10, 0))
                .status(ExperimentStatus.COMPLETED)
                .protocol(protocol)
                .measurementPairs(new java.util.ArrayList<>(List.of(calPair, badPair)))
                .usedReagentBatches(List.of())
                .build();

        CurveParameters params = new CurveParameters(Map.of("slope", 2.0, "intercept", 1.0));
        ValidationResult okResult = new ValidationResult(ExperimentStatus.OK, List.of(), params);

        when(experimentRepository.findById(1L)).thenReturn(Optional.of(experimentWithPairs));
        when(curveFittingService.fitCurve(any(), anyList())).thenReturn(params);
        when(objectMapper.writeValueAsString(params)).thenReturn("{}");
        // OutlierDetectionService flags pair id=2
        when(outlierDetectionService.detectOutliers(anyList(), any(Protocol.class)))
                .thenReturn(List.of(2L));
        when(validationEngine.evaluate(any(), any(), any())).thenReturn(okResult);
        when(experimentRepository.save(any(Experiment.class))).thenReturn(experimentWithPairs);

        experimentService.validate(1L);

        // The flagged pair must have isOutlier set to true
        assertThat(badPair.getIsOutlier()).isTrue();

        // An audit entry must be produced for the auto-flag
        verify(auditLogService).logChange(
                "MeasurementPair", 2L, "isOutlier",
                "false", "true", "SYSTEM:outlier-detection");

        // ValidationEngine must still be called (outlier is already set, engine skips it)
        verify(validationEngine).evaluate(any(), any(), eq(params));
    }

    /**
     * Validates that re-validating a terminal experiment (OK or KO) is rejected with 409.
     */
    @Test
    void validate_shouldThrow_whenExperimentAlreadyTerminal() {
        Experiment terminalExperiment = Experiment.builder()
                .id(1L)
                .name("Done")
                .date(LocalDateTime.now())
                .status(ExperimentStatus.OK)
                .protocol(protocol)
                .measurementPairs(List.of())
                .usedReagentBatches(List.of())
                .build();

        when(experimentRepository.findById(1L)).thenReturn(Optional.of(terminalExperiment));

        assertThatThrownBy(() -> experimentService.validate(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("terminal status");
        verify(curveFittingService, never()).fitCurve(any(), anyList());
    }

    // -------------------------------------------------------------------------
    // importCsv() tests
    // -------------------------------------------------------------------------

    /**
     * A valid CSV import must:
     * <ol>
     *   <li>call {@link CsvImportService#parse} with the file's stream</li>
     *   <li>persist the returned pairs via {@link MeasurementPairService#saveAll}</li>
     *   <li>return an {@link ExperimentResponse} with the experiment ID</li>
     * </ol>
     */
    @Test
    void importCsv_shouldParseSaveAndReturnResponse() throws Exception {
        MeasurementPair calPair = MeasurementPair.builder()
                .id(10L)
                .pairType(PairType.CALIBRATION)
                .concentrationNominal(1.0)
                .signal1(0.45).signal2(0.47)
                .signalMean(0.46).cvPct(0.5)
                .isOutlier(false)
                .build();

        Experiment experimentWithPairs = Experiment.builder()
                .id(1L)
                .name("Test Experiment")
                .date(LocalDateTime.of(2026, 4, 5, 10, 0))
                .status(ExperimentStatus.COMPLETED)
                .protocol(protocol)
                .measurementPairs(new java.util.ArrayList<>())
                .usedReagentBatches(List.of())
                .build();

        byte[] csvBytes = "WellId,Signal1,Signal2\nA1,0.45,0.47".getBytes();
        MultipartFile file = new MockMultipartFile(
                "file", "data.csv", "text/csv", csvBytes);

        CsvImportConfig config = new CsvImportConfig(
                CsvFormat.GENERIC, "WellId", "Signal1", "Signal2",
                Map.of("A1", new WellMapping(PairType.CALIBRATION, 1.0)));

        MeasurementPairRequest pairReq = new MeasurementPairRequest(
                PairType.CALIBRATION, 1.0, 0.45, 0.47, null, false);

        when(experimentRepository.findById(1L)).thenReturn(Optional.of(experimentWithPairs));
        when(csvImportService.parse(any(), any())).thenReturn(List.of(pairReq));
        when(measurementPairService.saveAll(anyList())).thenReturn(List.of(calPair));

        ExperimentResponse result = experimentService.importCsv(1L, file, config);

        assertThat(result.id()).isEqualTo(1L);
        verify(csvImportService).parse(any(), eq(config));
        verify(measurementPairService).saveAll(anyList());
    }

    /**
     * An empty {@link MultipartFile} must throw {@link IllegalArgumentException} before
     * the CSV parser is ever invoked.
     */
    @Test
    void importCsv_shouldThrow_whenFileIsEmpty() throws Exception {
        Experiment exp = Experiment.builder()
                .id(1L).name("Test").date(LocalDateTime.now())
                .status(ExperimentStatus.COMPLETED).protocol(protocol)
                .measurementPairs(List.of()).usedReagentBatches(List.of())
                .build();

        MultipartFile emptyFile = new MockMultipartFile(
                "file", "empty.csv", "text/csv", new byte[0]);

        CsvImportConfig config = new CsvImportConfig(
                CsvFormat.GENERIC, "WellId", "Signal1", "Signal2", Map.of());

        when(experimentRepository.findById(1L)).thenReturn(Optional.of(exp));

        assertThatThrownBy(() -> experimentService.importCsv(1L, emptyFile, config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be empty");

        verify(csvImportService, never()).parse(any(), any());
    }

    /**
     * When the experiment does not exist, importCsv must throw
     * {@link ResourceNotFoundException} without calling the CSV parser.
     */
    @Test
    void importCsv_shouldThrow_whenExperimentNotFound() throws Exception {
        MultipartFile file = new MockMultipartFile(
                "file", "data.csv", "text/csv", "data".getBytes());
        CsvImportConfig config = new CsvImportConfig(
                CsvFormat.GENERIC, "WellId", "Signal1", "Signal2", Map.of());

        when(experimentRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> experimentService.importCsv(99L, file, config))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");

        verify(csvImportService, never()).parse(any(), any());
    }
}
