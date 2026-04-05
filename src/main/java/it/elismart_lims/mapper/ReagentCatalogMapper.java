package it.elismart_lims.mapper;

import it.elismart_lims.dto.ReagentCatalogRequest;
import it.elismart_lims.dto.ReagentCatalogResponse;
import it.elismart_lims.model.ReagentCatalog;

import java.util.List;

/**
 * Static mapper between ReagentCatalog entities and their DTOs.
 */
public final class ReagentCatalogMapper {

    private ReagentCatalogMapper() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Converts a ReagentCatalogRequest DTO into a ReagentCatalog entity.
     *
     * @param request the request payload
     * @return the built ReagentCatalog entity
     */
    public static ReagentCatalog toEntity(ReagentCatalogRequest request) {
        return ReagentCatalog.builder()
                .name(request.name())
                .manufacturer(request.manufacturer())
                .description(request.description())
                .build();
    }

    /**
     * Converts a ReagentCatalog entity into a ReagentCatalogResponse DTO.
     *
     * @param entity the ReagentCatalog entity
     * @return the response DTO
     */
    public static ReagentCatalogResponse toResponse(ReagentCatalog entity) {
        return ReagentCatalogResponse.builder()
                .withId(entity.getId())
                .withName(entity.getName())
                .withManufacturer(entity.getManufacturer())
                .withDescription(entity.getDescription())
                .build();
    }

    /**
     * Converts a list of ReagentCatalog entities into response DTOs.
     *
     * @param entities the list of ReagentCatalog entities
     * @return the list of response DTOs
     */
    public static List<ReagentCatalogResponse> toResponseList(List<ReagentCatalog> entities) {
        return entities.stream().map(ReagentCatalogMapper::toResponse).toList();
    }
}
