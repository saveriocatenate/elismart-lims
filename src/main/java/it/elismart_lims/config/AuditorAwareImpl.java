package it.elismart_lims.config;

import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Supplies the current "user" identity for JPA auditing ({@code @CreatedBy}).
 *
 * <p>The backend currently has no per-request authentication — all calls from the
 * Streamlit frontend arrive unauthenticated at the API level. The fixed value
 * {@code "system"} ensures the {@code created_by} column is always populated.</p>
 *
 * <p><strong>To-do when auth is added:</strong> replace the body of
 * {@link #getCurrentAuditor()} with logic that reads the authenticated principal
 * from the security context (e.g. via {@code SecurityContextHolder}).</p>
 */
@Component("auditorAwareImpl")
public class AuditorAwareImpl implements AuditorAware<String> {

    @Override
    public Optional<String> getCurrentAuditor() {
        return Optional.of("system");
    }
}
