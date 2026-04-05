package it.elismart_lims.mapper;

import it.elismart_lims.dto.ReagentCatalogRequest;
import it.elismart_lims.model.ReagentCatalog;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ReagentCatalogMapper}.
 */
class ReagentCatalogMapperTest {

    @Test
    void toEntity_shouldMapRequestToEntity() {
        var request = new ReagentCatalogRequest("Anti-IgG", "Sigma", "Goat anti-human IgG");

        var entity = ReagentCatalogMapper.toEntity(request);

        assertThat(entity.getName()).isEqualTo("Anti-IgG");
        assertThat(entity.getManufacturer()).isEqualTo("Sigma");
        assertThat(entity.getDescription()).isEqualTo("Goat anti-human IgG");
    }

    @Test
    void toResponse_shouldMapEntityToResponse() {
        var entity = ReagentCatalog.builder()
                .id(1L)
                .name("Anti-IgG")
                .manufacturer("Sigma")
                .description("Goat anti-human IgG")
                .build();

        var response = ReagentCatalogMapper.toResponse(entity);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.name()).isEqualTo("Anti-IgG");
    }

    @Test
    void toResponseList_shouldMapMultipleEntities() {
        var entities = List.of(
                ReagentCatalog.builder().id(1L).name("A").manufacturer("M1").build(),
                ReagentCatalog.builder().id(2L).name("B").manufacturer("M2").build()
        );

        var responses = ReagentCatalogMapper.toResponseList(entities);

        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).name()).isEqualTo("A");
        assertThat(responses.get(1).name()).isEqualTo("B");
    }
}
