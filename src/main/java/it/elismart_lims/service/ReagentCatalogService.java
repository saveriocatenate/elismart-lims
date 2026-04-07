package it.elismart_lims.service;

import it.elismart_lims.dto.ReagentCatalogResponse;
import it.elismart_lims.exception.model.ResourceNotFoundException;
import it.elismart_lims.mapper.ReagentCatalogMapper;
import it.elismart_lims.model.ReagentCatalog;
import it.elismart_lims.repository.ReagentCatalogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Business logic for ReagentCatalog operations.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReagentCatalogService {

    private final ReagentCatalogRepository reagentCatalogRepository;

    /**
     * Find all reagent catalogs with pagination.
     *
     * @param pageable the pagination and sorting information
     * @return a paginated list of ReagentCatalogResponse
     */
    @Transactional(readOnly = true)
    public Page<ReagentCatalogResponse> getAll(Pageable pageable) {
        return reagentCatalogRepository.findAll(pageable).map(ReagentCatalogMapper::toResponse);
    }

    /**
     * Find a reagent catalog by its ID and return the response DTO.
     *
     * @param id the reagent catalog ID
     * @return the found ReagentCatalogResponse
     * @throws ResourceNotFoundException if no reagent catalog exists with the given ID
     */
    @Transactional(readOnly = true)
    public ReagentCatalogResponse getById(Long id) {
        return ReagentCatalogMapper.toResponse(getEntityById(id));
    }

    /**
     * Find a reagent catalog entity by its ID.
     *
     * @param id the reagent catalog ID
     * @return the found ReagentCatalog entity
     * @throws ResourceNotFoundException if no reagent catalog exists with the given ID
     */
    @Transactional(readOnly = true)
    public ReagentCatalog getEntityById(Long id) {
        return reagentCatalogRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Reagent catalog not found with id: " + id));
    }

    /**
     * Create a new reagent catalog entry.
     *
     * <p>Rejects the insert if a reagent with the same name and manufacturer already exists
     * (case-insensitive comparison on both fields).</p>
     *
     * @param reagentCatalog the entity to persist
     * @return the saved ReagentCatalog entity
     * @throws IllegalArgumentException if a duplicate name+manufacturer combination is detected
     */
    @Transactional
    public ReagentCatalog create(ReagentCatalog reagentCatalog) {
        if (reagentCatalogRepository.existsByNameIgnoreCaseAndManufacturerIgnoreCase(
                reagentCatalog.getName(), reagentCatalog.getManufacturer())) {
            throw new IllegalArgumentException(
                    "A reagent named '" + reagentCatalog.getName()
                    + "' from manufacturer '" + reagentCatalog.getManufacturer()
                    + "' already exists in the catalog.");
        }
        log.info("Creating reagent catalog: {}", reagentCatalog.getName());
        ReagentCatalog saved = reagentCatalogRepository.save(reagentCatalog);
        log.info("Reagent catalog created with id: {}", saved.getId());
        return saved;
    }

    /**
     * Search reagent catalogs with optional partial-match filters on name and manufacturer.
     * A {@code null} or blank value for a parameter means "no filter on that field".
     *
     * @param name         partial name to filter on, or {@code null}/blank to skip
     * @param manufacturer partial manufacturer to filter on, or {@code null}/blank to skip
     * @param pageable     pagination and sorting information
     * @return a page of matching ReagentCatalogResponse DTOs
     */
    @Transactional(readOnly = true)
    public Page<ReagentCatalogResponse> search(String name, String manufacturer, Pageable pageable) {
        String nameParam = (name == null || name.isBlank()) ? null : name;
        String mfrParam = (manufacturer == null || manufacturer.isBlank()) ? null : manufacturer;
        return reagentCatalogRepository.search(nameParam, mfrParam, pageable)
                .map(ReagentCatalogMapper::toResponse);
    }

    /**
     * Delete a reagent catalog by its ID.
     *
     * @param id the reagent catalog ID
     * @throws ResourceNotFoundException if no reagent catalog exists with the given ID
     */
    @Transactional
    public void delete(Long id) {
        log.info("Deleting reagent catalog id: {}", id);
        if (!reagentCatalogRepository.existsById(id)) {
            throw new ResourceNotFoundException("Reagent catalog not found with id: " + id);
        }
        reagentCatalogRepository.deleteById(id);
        log.info("Reagent catalog deleted id: {}", id);
    }
}
