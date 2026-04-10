package it.elismart_lims.service.validation;

import it.elismart_lims.model.Experiment;
import it.elismart_lims.model.ExperimentStatus;
import it.elismart_lims.model.MeasurementPair;
import it.elismart_lims.model.PairType;
import it.elismart_lims.model.Protocol;
import it.elismart_lims.service.curve.CurveFittingService;
import it.elismart_lims.service.curve.CurveParameters;
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
     * @param experiment  the experiment containing the measurement pairs to validate
     * @param protocol    the protocol defining {@code maxCvAllowed} and {@code maxErrorAllowed}
     * @param curveParams the calibration curve parameters produced by
     *                    {@link CurveFittingService#fitCurve} for this experiment
     * @return a {@link ValidationResult} with overall status and per-pair detail
     * @throws IllegalArgumentException if no CALIBRATION pairs are found in the experiment
     */
    public ValidationResult evaluate(Experiment experiment, Protocol protocol, CurveParameters curveParams) {
        boolean hasCalibration = experiment.getMeasurementPairs().stream()
                .anyMatch(p -> p.getPairType() == PairType.CALIBRATION);

        if (!hasCalibration) {
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
                continue;
            }

            double interpolated = curveFittingService.interpolateConcentration(
                    protocol.getCurveType(), pair.getSignalMean(), curveParams);

            double recovery = (interpolated / pair.getConcentrationNominal()) * 100.0;
            pair.setRecoveryPct(recovery);

            boolean cvPass = pair.getCvPct() == null
                    || pair.getCvPct() <= protocol.getMaxCvAllowed();

            boolean recoveryPass = Math.abs(recovery - 100.0) <= protocol.getMaxErrorAllowed();

            if (!cvPass || !recoveryPass) {
                allPass = false;
            }

            pairResults.add(new PairValidationResult(
                    pair.getId(), cvPass, recoveryPass, interpolated, recovery));
        }

        ExperimentStatus overallStatus = allPass ? ExperimentStatus.OK : ExperimentStatus.KO;
        return new ValidationResult(overallStatus, pairResults, curveParams);
    }
}
