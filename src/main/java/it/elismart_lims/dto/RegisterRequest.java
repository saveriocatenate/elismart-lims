package it.elismart_lims.dto;

import it.elismart_lims.model.UserRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for {@code POST /api/auth/register}.
 *
 * <p>All fields are mandatory:
 * <ul>
 *   <li>{@code username} and {@code password} use {@code @NotBlank} to reject null,
 *       empty, and whitespace-only strings.</li>
 *   <li>{@code role} uses {@code @NotNull} to ensure a valid {@link UserRole} is provided.</li>
 * </ul>
 * </p>
 *
 * @param username the unique login name for the new user
 * @param password the plaintext password (BCrypt-hashed before persistence)
 * @param role     the role to assign to the new user
 */
public record RegisterRequest(
        @NotBlank String username,
        @NotBlank String password,
        @NotNull UserRole role
) {
}
