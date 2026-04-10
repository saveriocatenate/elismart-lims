package it.elismart_lims.service.validation;

import it.elismart_lims.model.ExperimentStatus;
import it.elismart_lims.service.curve.CurveParameters;

import java.util.List;

/**
 * Immutable outcome of a full experiment validation run performed by {@link ValidationEngine}.
 *
 * <p>Contains the aggregate status, per-pair detail, and the curve parameters used during
 * back-interpolation so callers can persist them to the experiment entity.</p>
 *
 * @param overallStatus {@link ExperimentStatus#OK} if every evaluated pair passed;
 *                      {@link ExperimentStatus#KO} if at least one failed
 * @param pairResults   one {@link PairValidationResult} per evaluated (non-outlier, non-calibration) pair
 * @param fittedParams  the {@link CurveParameters} supplied to (and used by) the engine
 */
public record ValidationResult(
        ExperimentStatus overallStatus,
        List<PairValidationResult> pairResults,
        CurveParameters fittedParams
) {}
