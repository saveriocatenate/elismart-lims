package it.elismart_lims.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Enables Spring Data JPA auditing, which drives the {@code @CreatedDate},
 * {@code @LastModifiedDate}, and {@code @CreatedBy} annotations in
 * {@link it.elismart_lims.model.Auditable}.
 *
 * <p>This configuration is unconditional. {@code @ConditionalOnBean} was removed
 * because it is evaluated before JPA auto-configuration runs (user {@code @Configuration}
 * classes are processed first), so the {@code entityManagerFactory} bean is never found
 * and auditing would silently be disabled, causing {@code NOT NULL} constraint
 * violations on {@code created_at} and {@code created_by}.</p>
 *
 * <p>{@code @WebMvcTest} slices do not load JPA entities, so the
 * {@link org.springframework.data.jpa.domain.support.AuditingEntityListener}
 * registered here never fires in that context — no extra configuration is needed
 * in the test layer.</p>
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorAwareImpl")
public class JpaAuditingConfig {
}
