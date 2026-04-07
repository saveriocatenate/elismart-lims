package it.elismart_lims.repository;

import it.elismart_lims.model.ReagentCatalog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * JPA repository for ReagentCatalog entities.
 */
public interface ReagentCatalogRepository extends JpaRepository<ReagentCatalog, Long> {

    /**
     * Check whether a reagent with the given name and manufacturer already exists,
     * using a case-insensitive comparison on both fields.
     *
     * @param name         the reagent name to check
     * @param manufacturer the manufacturer to check
     * @return {@code true} if a matching entry exists
     */
    boolean existsByNameIgnoreCaseAndManufacturerIgnoreCase(String name, String manufacturer);

    /**
     * Search reagents with optional partial-match filters on name and manufacturer.
     * Both filters are case-insensitive. A {@code null} value for a parameter means
     * "no filter on that field".
     *
     * @param name         partial name to filter on, or {@code null} to skip
     * @param manufacturer partial manufacturer to filter on, or {@code null} to skip
     * @param pageable     pagination and sorting information
     * @return a page of matching ReagentCatalog entities
     */
    @Query("SELECT r FROM ReagentCatalog r WHERE " +
           "(:name IS NULL OR LOWER(r.name) LIKE LOWER(CONCAT('%', :name, '%'))) AND " +
           "(:manufacturer IS NULL OR LOWER(r.manufacturer) LIKE LOWER(CONCAT('%', :manufacturer, '%')))")
    Page<ReagentCatalog> search(
            @Param("name") String name,
            @Param("manufacturer") String manufacturer,
            Pageable pageable);
}