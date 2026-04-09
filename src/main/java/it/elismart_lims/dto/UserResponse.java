package it.elismart_lims.dto;

import it.elismart_lims.model.UserRole;
import lombok.Builder;

import java.time.LocalDateTime;

/**
 * Response DTO for {@link it.elismart_lims.model.User} entities.
 *
 * <p>The {@code password} field is intentionally absent — BCrypt hashes must
 * never be returned to callers.</p>
 */
@Builder
public record UserResponse(
        Long id,
        String username,
        UserRole role,
        boolean enabled,
        LocalDateTime createdAt
) {
}
