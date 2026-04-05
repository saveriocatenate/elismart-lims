package it.elismart_lims.repository;

import it.elismart_lims.model.ReagentCatalog;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * JPA repository for ReagentCatalog entities.
 */
public interface ReagentCatalogRepository extends JpaRepository<ReagentCatalog, Long> {
}