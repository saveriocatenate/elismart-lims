package it.elismart_lims.model;

/**
 * Valid lifecycle and validation statuses for an {@link Experiment}.
 *
 * <ul>
 *   <li>{@link #PENDING} – created but not yet run on the bench</li>
 *   <li>{@link #OK} – completed and all validation criteria passed</li>
 *   <li>{@link #KO} – completed but one or more validation criteria failed</li>
 *   <li>{@link #VALIDATION_ERROR} – an error occurred during automated validation</li>
 *   <li>{@link #COMPLETED} – run finished; validation outcome not yet determined</li>
 * </ul>
 */
public enum ExperimentStatus {
    PENDING,
    COMPLETED,
    OK,
    KO,
    VALIDATION_ERROR
}
