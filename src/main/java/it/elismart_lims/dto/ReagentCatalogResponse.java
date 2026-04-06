package it.elismart_lims.dto;

import lombok.Builder;

/**
 * Response payload for ReagentCatalog entities.
 */
@Builder
public record ReagentCatalogResponse(
        Long id,
        String name,
        String manufacturer,
        String description
) {
}
