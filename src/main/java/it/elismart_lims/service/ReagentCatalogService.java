package it.elismart_lims.service;

import it.elismart_lims.dto.ReagentCatalogResponse;
import it.elismart_lims.exception.model.ResourceNotFoundException;
import it.elismart_lims.mapper.ReagentCatalogMapper;
import it.elismart_lims.model.ReagentCatalog;
import it.elismart_lims.repository.ReagentCatalogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Business logic for ReagentCatalog operations.
 */
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
     * Create a new reagent catalog.
     *
     * @param reagentCatalog the entity to persist
     * @return the saved ReagentCatalog entity
     */
    @Transactional
    public ReagentCatalog create(ReagentCatalog reagentCatalog) {
        return reagentCatalogRepository.save(reagentCatalog);
    }

    /**
     * Delete a reagent catalog by its ID.
     *
     * @param id the reagent catalog ID
     * @throws ResourceNotFoundException if no reagent catalog exists with the given ID
     */
    @Transactional
    public void delete(Long id) {
        if (!reagentCatalogRepository.existsById(id)) {
            throw new ResourceNotFoundException("Reagent catalog not found with id: " + id);
        }
        reagentCatalogRepository.deleteById(id);
    }
}
