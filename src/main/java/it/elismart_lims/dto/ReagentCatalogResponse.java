package it.elismart_lims.dto;

/**
 * Response payload for ReagentCatalog entities.
 */
public record ReagentCatalogResponse(
        Long id,
        String name,
        String manufacturer,
        String description
) {
    public static final class Builder {
        private Long id;
        private String name;
        private String manufacturer;
        private String description;
        public Builder withId(Long id) {
            this.id = id;
            return this;
        }
        public Builder withName(String name) {
            this.name = name;
            return this;
        }
        public Builder withManufacturer(String manufacturer) {
            this.manufacturer = manufacturer;
            return this;
        }
        public Builder withDescription(String description) {
            this.description = description;
            return this;
        }
        public ReagentCatalogResponse build() {
            return new ReagentCatalogResponse(id, name, manufacturer, description);
        }

    }
    public static Builder builder() {
        return new Builder();
    }
}
