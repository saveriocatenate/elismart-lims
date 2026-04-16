package it.elismart_lims.mapper;

import it.elismart_lims.dto.MeasurementPairRequest;
import it.elismart_lims.dto.MeasurementPairResponse;
import it.elismart_lims.dto.SampleResponse;
import it.elismart_lims.model.Experiment;
import it.elismart_lims.model.ExperimentStatus;
import it.elismart_lims.model.MeasurementPair;
import it.elismart_lims.model.PairStatus;
import it.elismart_lims.model.PairType;
import it.elismart_lims.model.Protocol;
import it.elismart_lims.service.validation.ValidationConstants;

import java.util.List;

/**
 * Static mapper between MeasurementPair entities and their DTOs.
 */
public final class MeasurementPairMapper {

    private MeasurementPairMapper() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Converts a MeasurementPairRequest DTO into a MeasurementPair entity.
     *
     * @param request the request payload
     * @return the built MeasurementPair entity
     */
    public static MeasurementPair toEntity(MeasurementPairRequest request) {
        return toEntity(request, null);
    }

    /**
     * Converts a MeasurementPairRequest DTO into a MeasurementPair entity linked to an experiment.
     *
     * <p>{@code signalMean} and {@code cvPct} are always computed server-side from the raw signals
     * using {@link ValidationConstants}. Any client-supplied values for these derived fields are
     * ignored per the server-side derivation policy.</p>
     *
     * @param request    the request payload
     * @param experiment the experiment to associate with
     * @return the built MeasurementPair entity
     */
    public static MeasurementPair toEntity(MeasurementPairRequest request, Experiment experiment) {
        double s1 = request.signal1();
        double s2 = request.signal2();
        return MeasurementPair.builder()
                .pairType(request.pairType())
                .concentrationNominal(request.concentrationNominal())
                .signal1(s1)
                .signal2(s2)
                .signalMean(ValidationConstants.calculateSignalMean(s1, s2))  // always server-side
                .cvPct(ValidationConstants.calculateCvPercent(s1, s2))        // always server-side
                .recoveryPct(null)                                             // always server-side; client-supplied value ignored
                .experiment(experiment)
                .isOutlier(request.isOutlier() != null ? request.isOutlier() : false)
                .build();
    }

    /**
     * Converts a list of MeasurementPairRequest DTOs into entities linked to an experiment.
     *
     * @param requests   the list of request payloads
     * @param experiment the experiment to associate with
     * @return the list of built MeasurementPair entities
     */
    public static List<MeasurementPair> toEntityList(List<MeasurementPairRequest> requests, Experiment experiment) {
        return requests.stream()
                .map(req -> toEntity(req, experiment))
                .toList();
    }

    /**
     * Converts a MeasurementPair entity into a MeasurementPairResponse DTO without protocol
     * context. {@code pairStatus} will be {@code null} in the returned DTO.
     *
     * <p>Use this overload only when protocol limits are not available (e.g. a PATCH /outlier
     * response). Prefer {@link #toResponse(MeasurementPair, Protocol, ExperimentStatus)} when
     * returning pairs as part of a full experiment detail.</p>
     *
     * @param entity the MeasurementPair entity
     * @return the response DTO with {@code pairStatus = null}
     */
    public static MeasurementPairResponse toResponse(MeasurementPair entity) {
        return buildResponse(entity, null);
    }

    /**
     * Converts a MeasurementPair entity into a MeasurementPairResponse DTO, computing
     * {@link PairStatus} from the protocol limits and current experiment status.
     *
     * <p>Status computation rules (in priority order):
     * <ol>
     *   <li>If {@code isOutlier == true} → {@link PairStatus#OUTLIER}</li>
     *   <li>If the experiment status is PENDING, COMPLETED, or VALIDATION_ERROR (not yet fully
     *       evaluated) → {@link PairStatus#PENDING}</li>
     *   <li>If {@code cvPct > protocol.maxCvAllowed} → {@link PairStatus#FAIL}</li>
     *   <li>For CONTROL/SAMPLE pairs: if {@code recoveryPct != null} and
     *       {@code |recoveryPct − 100| > protocol.maxErrorAllowed} → {@link PairStatus#FAIL}</li>
     *   <li>For CONTROL/SAMPLE pairs: if {@code recoveryPct == null} and
     *       {@code concentrationNominal > 0} → {@link PairStatus#FAIL} (recovery was expected
     *       but is missing — anomalous state).</li>
     *   <li>Otherwise → {@link PairStatus#PASS}</li>
     * </ol>
     * CALIBRATION pairs skip the recovery check entirely; recovery is not a meaningful metric
     * for calibration points (they define the curve, not a target concentration).</p>
     *
     * @param entity           the MeasurementPair entity
     * @param protocol         the owning experiment's protocol (provides limit thresholds)
     * @param experimentStatus the current status of the owning experiment
     * @return the response DTO with a computed {@code pairStatus}
     */
    public static MeasurementPairResponse toResponse(MeasurementPair entity, Protocol protocol,
                                                     ExperimentStatus experimentStatus) {
        return buildResponse(entity, computePairStatus(entity, protocol, experimentStatus));
    }

    /**
     * Converts a list of MeasurementPair entities into response DTOs without protocol context.
     * {@code pairStatus} will be {@code null} for every item.
     *
     * @param entities the list of MeasurementPair entities
     * @return the list of response DTOs
     */
    public static List<MeasurementPairResponse> toResponseList(List<MeasurementPair> entities) {
        return entities.stream().map(MeasurementPairMapper::toResponse).toList();
    }

    /**
     * Converts a list of MeasurementPair entities into response DTOs, computing
     * {@link PairStatus} for each pair from the shared protocol limits and experiment status.
     *
     * @param entities         the list of MeasurementPair entities
     * @param protocol         the owning experiment's protocol
     * @param experimentStatus the current status of the owning experiment
     * @return the list of response DTOs with computed {@code pairStatus}
     */
    public static List<MeasurementPairResponse> toResponseList(List<MeasurementPair> entities,
                                                               Protocol protocol,
                                                               ExperimentStatus experimentStatus) {
        return entities.stream()
                .map(e -> toResponse(e, protocol, experimentStatus))
                .toList();
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Shared builder logic for both {@code toResponse} overloads.
     *
     * @param entity     the source entity
     * @param pairStatus the pre-computed status, or {@code null} when unavailable
     * @return the assembled response DTO
     */
    private static MeasurementPairResponse buildResponse(MeasurementPair entity, PairStatus pairStatus) {
        SampleResponse sampleResponse = entity.getSample() != null
                ? SampleMapper.toResponse(entity.getSample())
                : null;
        return MeasurementPairResponse.builder()
                .id(entity.getId())
                .pairType(entity.getPairType())
                .concentrationNominal(entity.getConcentrationNominal())
                .signal1(entity.getSignal1())
                .signal2(entity.getSignal2())
                .signalMean(entity.getSignalMean())
                .cvPct(entity.getCvPct())
                .recoveryPct(entity.getRecoveryPct())
                .isOutlier(entity.getIsOutlier() != null && entity.getIsOutlier())
                .sample(sampleResponse)
                .pairStatus(pairStatus)
                .build();
    }

    /**
     * Computes the {@link PairStatus} for a single pair given the protocol limits and
     * experiment validation state.
     *
     * @param pair             the measurement pair to evaluate
     * @param protocol         the protocol providing acceptance thresholds
     * @param experimentStatus the current experiment lifecycle status
     * @return the computed {@link PairStatus}
     */
    private static PairStatus computePairStatus(MeasurementPair pair, Protocol protocol,
                                                ExperimentStatus experimentStatus) {
        // Priority 1: outlier flag overrides everything else.
        if (Boolean.TRUE.equals(pair.getIsOutlier())) {
            return PairStatus.OUTLIER;
        }

        // Priority 2: experiment not yet fully validated — no per-pair verdict possible.
        if (experimentStatus == ExperimentStatus.PENDING
                || experimentStatus == ExperimentStatus.COMPLETED
                || experimentStatus == ExperimentStatus.VALIDATION_ERROR) {
            return PairStatus.PENDING;
        }

        // Priority 3: %CV check (applies to all pair types).
        if (pair.getCvPct() != null && pair.getCvPct() > protocol.getMaxCvAllowed()) {
            return PairStatus.FAIL;
        }

        // Priority 4: %Recovery check (CALIBRATION pairs are exempt — they define the curve).
        if (pair.getPairType() != PairType.CALIBRATION) {
            Double recoveryPct = pair.getRecoveryPct();
            if (recoveryPct != null) {
                if (Math.abs(recoveryPct - 100.0) > protocol.getMaxErrorAllowed()) {
                    return PairStatus.FAIL;
                }
            } else if (pair.getConcentrationNominal() != null
                    && pair.getConcentrationNominal() > 0) {
                // Recovery was expected (positive nominal concentration) but was not computed.
                // This is an anomalous state: treat as FAIL so the scientist is alerted.
                return PairStatus.FAIL;
            }
            // concentrationNominal <= 0 → recovery was intentionally skipped (guard rule);
            // this is not an error, so we fall through to PASS.
        }

        return PairStatus.PASS;
    }
}
