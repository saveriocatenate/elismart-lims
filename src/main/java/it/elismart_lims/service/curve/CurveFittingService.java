package it.elismart_lims.service.curve;

import it.elismart_lims.model.CurveType;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Single entry point for all calibration curve fitting and back-interpolation operations.
 *
 * <p>Delegates to the appropriate {@link CurveFitter} implementation based on the
 * {@link CurveType} stored on the {@code Protocol}. External code (other services,
 * the validation engine) must go through this service — fitter implementations are
 * not exposed directly.</p>
 *
 * <p>The fitter registry is built once at construction time. All registered fitters
 * are stateless and safe for concurrent use.</p>
 */
@Service
public class CurveFittingService {

    private final Map<CurveType, CurveFitter> fitters;

    /**
     * Constructs the service and registers one {@link CurveFitter} per {@link CurveType}.
     *
     * <table>
     *   <caption>Registered fitters</caption>
     *   <tr><th>CurveType</th><th>Implementation</th><th>Status</th></tr>
     *   <tr><td>FOUR_PARAMETER_LOGISTIC</td><td>{@link FourPLFitter}</td><td>Full</td></tr>
     *   <tr><td>FIVE_PARAMETER_LOGISTIC</td><td>{@link FivePLFitter}</td><td>Stub (throws)</td></tr>
     *   <tr><td>LOG_LOGISTIC_3P</td><td>{@link LogLogistic3PFitter}</td><td>Full</td></tr>
     *   <tr><td>LINEAR</td><td>{@link LinearFitter}</td><td>Full</td></tr>
     *   <tr><td>SEMI_LOG_LINEAR</td><td>{@link SemiLogLinearFitter}</td><td>Full</td></tr>
     *   <tr><td>POINT_TO_POINT</td><td>{@link PointToPointFitter}</td><td>Full</td></tr>
     * </table>
     */
    public CurveFittingService() {
        Map<CurveType, CurveFitter> map = new EnumMap<>(CurveType.class);
        map.put(CurveType.FOUR_PARAMETER_LOGISTIC, new FourPLFitter());
        map.put(CurveType.FIVE_PARAMETER_LOGISTIC, new FivePLFitter());
        map.put(CurveType.LOG_LOGISTIC_3P, new LogLogistic3PFitter());
        map.put(CurveType.LINEAR, new LinearFitter());
        map.put(CurveType.SEMI_LOG_LINEAR, new SemiLogLinearFitter());
        map.put(CurveType.POINT_TO_POINT, new PointToPointFitter());
        this.fitters = Collections.unmodifiableMap(map);
    }

    /**
     * Fits a calibration curve of the given type to the supplied calibration points.
     *
     * @param type   the curve model to fit
     * @param points the calibration points (concentration, signal pairs)
     * @return the fitted {@link CurveParameters}
     * @throws UnsupportedOperationException if the fitter for {@code type} is not yet implemented
     * @throws IllegalArgumentException      if {@code points} does not satisfy the model's
     *                                       minimum point requirement
     */
    public CurveParameters fitCurve(CurveType type, List<CalibrationPoint> points) {
        return getFitter(type).fit(points);
    }

    /**
     * Back-calculates the concentration from a measured signal using pre-fitted parameters.
     *
     * @param type   the curve model (must match the model used during {@link #fitCurve})
     * @param signal the measured instrument signal
     * @param params the fitted curve parameters returned by {@link #fitCurve}
     * @return the interpolated concentration
     * @throws UnsupportedOperationException if the fitter for {@code type} is not yet implemented
     * @throws IllegalArgumentException      if {@code signal} is outside the interpolable range
     */
    public double interpolateConcentration(CurveType type, double signal, CurveParameters params) {
        return getFitter(type).interpolate(signal, params);
    }

    /**
     * Resolves the {@link CurveFitter} for the given {@link CurveType}.
     *
     * @param type the requested curve type
     * @return the registered fitter
     * @throws UnsupportedOperationException if no fitter is registered for {@code type}
     *         (should never happen unless a new enum value is added without registering a fitter)
     */
    private CurveFitter getFitter(CurveType type) {
        CurveFitter fitter = fitters.get(type);
        if (fitter == null) {
            throw new UnsupportedOperationException(
                    "No CurveFitter registered for CurveType: " + type
                    + ". Register a fitter in CurveFittingService constructor.");
        }
        return fitter;
    }
}
