package it.elismart_lims.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Enables Spring Data JPA auditing, which drives the {@code @CreatedDate},
 * {@code @LastModifiedDate}, and {@code @CreatedBy} annotations in
 * {@link it.elismart_lims.model.Auditable}.
 *
 * <p>{@code @ConditionalOnBean} restricts activation to contexts that have a
 * fully configured JPA {@code EntityManagerFactory}. This prevents the
 * auditing infrastructure from being registered during {@code @WebMvcTest}
 * slices, which do not load JPA at all.</p>
 */
@Configuration
@ConditionalOnBean(name = "entityManagerFactory")
@EnableJpaAuditing(auditorAwareRef = "auditorAwareImpl")
public class JpaAuditingConfig {
}
