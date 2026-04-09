package it.elismart_lims.model;

/**
 * Mathematical model used to fit the calibration curve of a {@link Protocol}.
 *
 * <p>Each constant carries a human-readable {@link #displayName}, a {@link #description}
 * suitable for display in the UI, and {@link #requiredParameters} which records how many
 * free parameters the fitting algorithm must estimate (used by the future curve-fit engine).</p>
 */
public enum CurveType {

    /**
     * Four Parameter Logistic — the de-facto standard for ELISAs.
     * Describes a symmetric sigmoid curve.
     * Parameters: Minimum, Maximum, IC50 (inflection point), Slope (Hill Slope).
     */
    FOUR_PARAMETER_LOGISTIC(
            "4PL",
            "Symmetric sigmoid curve (ELISA standard). Parameters: Minimum, Maximum, IC50, Slope.",
            4),

    /**
     * Five Parameter Logistic — extends 4PL with an asymmetry parameter.
     * Recommended when the top and bottom of the sigmoid are not mirrored.
     * Parameters: Minimum, Maximum, IC50, Slope, Asymmetry.
     */
    FIVE_PARAMETER_LOGISTIC(
            "5PL",
            "Asymmetric sigmoid curve. Parameters: Minimum, Maximum, IC50, Slope, Asymmetry.",
            5),

    /**
     * Three Parameter Log-Logistic — simplified 4PL where the minimum (background) is fixed at zero.
     * Parameters: Maximum, IC50, Slope.
     */
    LOG_LOGISTIC_3P(
            "3PL",
            "Simplified 4PL with minimum fixed at zero. Parameters: Maximum, IC50, Slope.",
            3),

    /**
     * Simple linear regression {@code y = mx + q}.
     * Suitable only for narrow concentration ranges.
     * Parameters: slope (m), intercept (q).
     */
    LINEAR(
            "Linear",
            "Simple linear regression y = mx + q. Parameters: slope, intercept.",
            2),

    /**
     * Linear fit with log-transformed X-axis (concentration).
     * Useful for data visualization but less precise for recovery calculation.
     * Parameters: slope (m), intercept (q) on the log-concentration scale.
     */
    SEMI_LOG_LINEAR(
            "Semi-log Linear",
            "Linear regression with log-transformed X-axis (concentration). Parameters: slope, intercept.",
            2),

    /**
     * Non-parametric point-to-point interpolation — connects calibration points with broken segments.
     * Legacy approach; not recommended for high-precision analysis.
     * No model parameters are estimated.
     */
    POINT_TO_POINT(
            "Point-to-Point",
            "Non-parametric interpolation between calibration points. Not recommended for high-precision analysis.",
            0);

    /** Short, human-readable label shown in the UI (e.g. "4PL"). */
    private final String displayName;

    /** Full description of the curve model. */
    private final String description;

    /**
     * Number of free parameters the fitting algorithm must estimate.
     * Zero means no mathematical fitting is performed (point-to-point interpolation).
     */
    private final int requiredParameters;

    CurveType(String displayName, String description, int requiredParameters) {
        this.displayName = displayName;
        this.description = description;
        this.requiredParameters = requiredParameters;
    }

    /**
     * Returns the short display name (e.g. {@code "4PL"}).
     *
     * @return the display name
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Returns the full description of the model.
     *
     * @return the description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Returns the number of free parameters required by the fitting algorithm.
     *
     * @return the number of required parameters
     */
    public int getRequiredParameters() {
        return requiredParameters;
    }
}
