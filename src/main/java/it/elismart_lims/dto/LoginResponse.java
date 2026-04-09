package it.elismart_lims.dto;

import lombok.Builder;

/**
 * Response DTO for a successful {@code POST /api/auth/login}.
 *
 * <p>Contains the signed JWT the client must include in subsequent requests
 * as {@code Authorization: Bearer <token>}, together with enough user context
 * for the frontend to display role-specific UI without an extra round-trip.</p>
 *
 * @param token    the signed JWT string
 * @param username the authenticated user's login name
 * @param role     the authenticated user's role (e.g. {@code ADMIN}, {@code ANALYST})
 */
@Builder
public record LoginResponse(
        String token,
        String username,
        String role
) {
}
