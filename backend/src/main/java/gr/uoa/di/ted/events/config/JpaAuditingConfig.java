package gr.uoa.di.ted.events.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Turns on Spring Data JPA auditing so @CreatedDate and @LastModifiedDate on
 * BaseEntity subclasses are filled in automatically. An AuditorAware bean (for
 * @CreatedBy/@LastModifiedBy) could be added later if we need it.
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
