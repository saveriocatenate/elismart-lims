package it.elismart_lims.service.curve;

import java.util.List;

/**
 * Five-Parameter Logistic (5PL) calibration curve fitter — stub implementation.
 *
 * <h2>Model</h2>
 * <pre>
 *   y = D + (A − D) / (1 + (x / C)^B)^E
 * </pre>
 * <ul>
 *   <li><b>A</b> — bottom asymptote</li>
 *   <li><b>B</b> — slope (Hill coefficient)</li>
 *   <li><b>C</b> — inflection point (EC50 / IC50)</li>
 *   <li><b>D</b> — top asymptote</li>
 *   <li><b>E</b> — asymmetry parameter; when E = 1 the model reduces to 4PL</li>
 * </ul>
 *
 * <p><b>This implementation is a stub.</b> Both {@link #fit} and {@link #interpolate}
 * throw {@link UnsupportedOperationException} until full Levenberg-Marquardt fitting
 * for 5 parameters is implemented.</p>
 */
public class FivePLFitter implements CurveFitter {

    /**
     * Not yet implemented.
     *
     * @throws UnsupportedOperationException always
     */
    @Override
    public CurveParameters fit(List<CalibrationPoint> points) {
        throw new UnsupportedOperationException("5PL fitting not yet implemented");
    }

    /**
     * Not yet implemented.
     *
     * @throws UnsupportedOperationException always
     */
    @Override
    public double interpolate(double signal, CurveParameters params) {
        throw new UnsupportedOperationException("5PL fitting not yet implemented");
    }
}
