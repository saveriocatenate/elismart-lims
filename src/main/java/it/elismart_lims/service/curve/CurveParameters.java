package it.elismart_lims.service.curve;

import java.util.Map;

/**
 * Immutable container for the fitted parameters of a calibration curve.
 *
 * <p>Parameter names are model-specific. For the 4PL model the keys are
 * {@code "A"}, {@code "B"}, {@code "C"}, and {@code "D"}. Consumers should
 * retrieve values via the constant keys exposed by the fitter implementation
 * (e.g. {@code FourPLFitter.PARAM_A}).</p>
 *
 * <p>Instances are produced by {@link CurveFitter#fit} and consumed by
 * {@link CurveFitter#interpolate}. They are also serialised as JSON to the
 * {@code curve_parameters} column on the {@code experiment} table.</p>
 *
 * @param values immutable map of parameter name → fitted numeric value
 */
public record CurveParameters(Map<String, Double> values) {}
