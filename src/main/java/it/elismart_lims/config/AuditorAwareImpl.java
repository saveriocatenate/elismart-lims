package it.elismart_lims.config;

import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Supplies the current authenticated username for JPA auditing ({@code @CreatedBy},
 * {@code @LastModifiedBy}).
 *
 * <p>Reads the principal from {@link SecurityContextHolder}. Falls back to
 * {@code "system"} for unauthenticated requests (e.g., Flyway callbacks, scheduled
 * tasks) and anonymous accesses.</p>
 */
@Component("auditorAwareImpl")
public class AuditorAwareImpl implements AuditorAware<String> {

    /** Fallback value used when no authenticated principal is present. */
    private static final String SYSTEM = "system";

    @Override
    public Optional<String> getCurrentAuditor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getPrincipal())) {
            return Optional.of(SYSTEM);
        }

        return Optional.of(authentication.getName());
    }
}
