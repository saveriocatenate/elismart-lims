package it.elismart_lims.dto;

import it.elismart_lims.model.UserRole;
import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for {@code PUT /api/users/{id}/role}.
 *
 * @param role the new role to assign to the target user; must not be {@code null}
 */
public record RoleUpdateRequest(
        @NotNull UserRole role
) {
}
