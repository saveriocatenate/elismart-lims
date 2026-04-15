package it.elismart_lims.service.validation;

import it.elismart_lims.model.Experiment;
import it.elismart_lims.model.ExperimentStatus;
import it.elismart_lims.model.MeasurementPair;
import it.elismart_lims.model.PairType;
import it.elismart_lims.model.Protocol;
import it.elismart_lims.service.curve.CurveFittingService;
import it.elismart_lims.service.curve.CurveParameters;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Core validation engine that evaluates an experiment against its protocol acceptance criteria.
 *
 * <h2>Workflow</h2>
 * <ol>
 *   <li>Verifies that at least one CALIBRATION pair is present (required to have produced
 *       the supplied {@link CurveParameters}).</li>
 *   <li>For every non-outlier CONTROL and SAMPLE pair:
 *     <ul>
 *       <li>Back-interpolates the concentration from {@code signalMean} using
 *           {@link CurveFittingService#interpolateConcentration}.</li>
 *       <li>Calculates {@code recoveryPct = (interpolatedConc / concentrationNominal) × 100}
 *           and writes it back to the {@link MeasurementPair}.</li>
 *       <li>Compares {@code cvPct} against {@link Protocol#getMaxCvAllowed()} (FAIL if exceeded).</li>
 *       <li>Compares {@code recoveryPct} against {@code 100 ± maxErrorAllowed} (FAIL if outside).</li>
 *     </ul>
 *   </li>
 *   <li>Returns {@link ExperimentStatus#OK} if every evaluated pair passed,
 *       {@link ExperimentStatus#KO} otherwise.</li>
 * </ol>
 *
 * <p>CALIBRATION pairs and outlier-flagged pairs are excluded from validation.
 * Manual status overrides must be handled externally and persisted via
 * {@link it.elismart_lims.service.audit.AuditLogService}.</p>
 */
@Slf4j
@Service
public class ValidationEngine {

    private final CurveFittingService curveFittingService;

    /**
     * Creates a {@code ValidationEngine} with its required {@link CurveFittingService} dependency.
     *
     * @param curveFittingService service used for back-interpolation of concentrations
     */
    public ValidationEngine(CurveFittingService curveFittingService) {
        this.curveFittingService = curveFittingService;
    }

    /**
     * Evaluates all non-outlier CONTROL and SAMPLE pairs in the experiment against the
     * protocol limits using the pre-fitted calibration curve parameters.
     *
     * <p>Side-effect: writes the calculated {@code recoveryPct} back onto each evaluated
     * {@link MeasurementPair}. The caller is responsible for persisting the experiment.</p>
     *
     * <p>Special cases handled without marking the pair KO on recovery grounds:</p>
     * <ul>
     *   <li><b>Zero / negative nominal concentration</b>: if {@code concentrationNominal} is
     *       {@code null} or {@code ≤ 0}, the recovery calculation is skipped, {@code recoveryPct}
     *       is set to {@code null}, and the pair is not penalised for recovery. A WARN is logged.</li>
     *   <li><b>Out-of-range signal</b>: if back-interpolation returns a negative concentration,
     *       the pair is flagged as out-of-calibration-range in its {@link PairValidationResult},
     *       {@code recoveryPct} is set to {@code null}, and {@code recoveryPass} is {@code false}.
     *       A WARN is logged.</li>
     * </ul>
     *
     * @param experiment  the experiment containing the measurement pairs to validate
     * @param protocol    the protocol defining {@code maxCvAllowed} and {@code maxErrorAllowed}
     * @param curveParams the calibration curve parameters produced by
     *                    {@link CurveFittingService#fitCurve} for this experiment
     * @return a {@link ValidationResult} with overall status and per-pair detail
     * @throws IllegalArgumentException if no CALIBRATION pairs are found in the experiment
     */
    public ValidationResult evaluate(Experiment experiment, Protocol protocol, CurveParameters curveParams) {
        log.info("Starting validation for experiment id={}, protocol='{}', curveType={}",
                experiment.getId(), protocol.getName(), protocol.getCurveType());

        boolean hasCalibration = experiment.getMeasurementPairs().stream()
                .anyMatch(p -> p.getPairType() == PairType.CALIBRATION);

        if (!hasCalibration) {
            log.error("Validation aborted for experiment id={}: no CALIBRATION pairs found", experiment.getId());
            throw new IllegalArgumentException(
                    "Cannot validate experiment id=" + experiment.getId()
                    + ": no CALIBRATION pairs found. At least one CALIBRATION pair is required "
                    + "to produce valid curve parameters for back-interpolation.");
        }

        List<PairValidationResult> pairResults = new ArrayList<>();
        boolean allPass = true;

        for (MeasurementPair pair : experiment.getMeasurementPairs()) {
            if (pair.getPairType() == PairType.CALIBRATION) {
                continue;
            }
            if (Boolean.TRUE.equals(pair.getIsOutlier())) {
                log.debug("Pair id={} skipped: flagged as outlier", pair.getId());
                continue;
            }

            // CASO 1: nominal concentration is null or non-positive — blank/zero calibrators are
            // legitimate; skip recovery calculation rather than dividing by zero or producing Infinity.
            if (pair.getConcentrationNominal() == null || pair.getConcentrationNominal() <= 0.0) {
                log.warn("Pair id={} has nominal concentration <= 0, recovery check skipped", pair.getId());
                pair.setRecoveryPct(null);
                boolean cvPassOnly = pair.getCvPct() == null
                        || pair.getCvPct() <= protocol.getMaxCvAllowed();
                if (!cvPassOnly) {
                    allPass = false;
                    log.debug("Pair id={} FAILED — cvPass=false (cvPct={}), recovery skipped",
                            pair.getId(), pair.getCvPct());
                } else {
                    log.debug("Pair id={} — recovery skipped (nominal ≤ 0), cvPct={} within limit",
                            pair.getId(), pair.getCvPct());
                }
                pairResults.add(new PairValidationResult(
                        pair.getId(), cvPassOnly, true, null, null, false));
                continue;
            }

            double interpolated = curveFittingService.interpolateConcentration(
                    protocol.getCurveType(), pair.getSignalMean(), curveParams);

            // CASO 2: back-interpolation produced a negative concentration — the signal lies
            // outside the calibration range; recovery is meaningless and must not be persisted.
            if (interpolated < 0) {
                log.warn("Pair id={} back-interpolation produced negative concentration ({}), "
                        + "signal may be outside calibration range", pair.getId(), interpolated);
                pair.setRecoveryPct(null);
                boolean cvPassOob = pair.getCvPct() == null
                        || pair.getCvPct() <= protocol.getMaxCvAllowed();
                allPass = false;
                log.debug("Pair id={} FAILED — out of calibration range, recoveryPass=false, cvPass={}",
                        pair.getId(), cvPassOob);
                pairResults.add(new PairValidationResult(
                        pair.getId(), cvPassOob, false, interpolated, null, true));
                continue;
            }

            double recovery = (interpolated / pair.getConcentrationNominal()) * 100.0;
            pair.setRecoveryPct(recovery);

            if (pair.getCvPct() == null) {
                log.info("Pair id={} has null %CV — treated as pass (cvPct not computed for this pair)",
                        pair.getId());
            }
            boolean cvPass = pair.getCvPct() == null
                    || pair.getCvPct() <= protocol.getMaxCvAllowed();

            boolean recoveryPass = Math.abs(recovery - 100.0) <= protocol.getMaxErrorAllowed();

            if (!cvPass || !recoveryPass) {
                allPass = false;
                log.debug("Pair id={} FAILED — cvPass={} (cvPct={}), recoveryPass={} (recovery={}%)",
                        pair.getId(), cvPass, pair.getCvPct(), recoveryPass, String.format("%.2f", recovery));
            } else {
                log.debug("Pair id={} PASSED — cvPct={}, recovery={}%",
                        pair.getId(), pair.getCvPct(), String.format("%.2f", recovery));
            }

            pairResults.add(new PairValidationResult(
                    pair.getId(), cvPass, recoveryPass, interpolated, recovery, false));
        }

        ExperimentStatus overallStatus = allPass ? ExperimentStatus.OK : ExperimentStatus.KO;
        log.info("Validation complete for experiment id={}: status={}, evaluated {} pairs",
                experiment.getId(), overallStatus, pairResults.size());
        return new ValidationResult(overallStatus, pairResults, curveParams);
    }
}
