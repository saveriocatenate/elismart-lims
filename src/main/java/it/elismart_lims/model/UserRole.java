package it.elismart_lims.model;

/**
 * Roles available to authenticated users.
 *
 * <ul>
 *   <li>{@link #ANALYST} — can create and edit experiments.</li>
 *   <li>{@link #REVIEWER} — can approve or reject finalised experiments.</li>
 *   <li>{@link #ADMIN} — full access: user management, protocol CRUD, delete operations.</li>
 * </ul>
 *
 * <p>The CHECK constraint in the Flyway migration mirrors this enum exactly.
 * Keep both in sync when adding new roles.</p>
 */
public enum UserRole {

    /** Laboratory analyst — create/edit experiments. */
    ANALYST,

    /** Reviewer — approve or reject completed experiments. */
    REVIEWER,

    /** Administrator — user management and unrestricted access. */
    ADMIN
}
