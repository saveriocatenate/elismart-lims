package it.elismart_lims.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for {@code POST /api/auth/login}.
 *
 * <p>Both fields are mandatory: {@code @NotBlank} rejects {@code null},
 * empty strings, and whitespace-only strings.</p>
 *
 * @param username the user's login name
 * @param password the user's plaintext password (never persisted)
 */
public record LoginRequest(
        @NotBlank String username,
        @NotBlank String password
) {
}
