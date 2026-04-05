package it.elismart_lims.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request payload for creating or updating a ReagentCatalog.
 */
public record ReagentCatalogRequest(
        @NotBlank String name,
        @NotBlank String manufacturer,
        String description
) {
}
