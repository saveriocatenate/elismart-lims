package it.elismart_lims.mapper;

import it.elismart_lims.model.ReagentCatalog;
import it.elismart_lims.model.UsedReagentBatch;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link UsedReagentBatchMapper}.
 */
class UsedReagentBatchMapperTest {

    @Test
    void toResponse_shouldMapEntityToResponse() {
        var reagent = ReagentCatalog.builder().id(1L).name("Anti-IgG").build();
        var entity = UsedReagentBatch.builder()
                .id(10L)
                .reagent(reagent)
                .lotNumber("LOT-001")
                .expiryDate(LocalDate.of(2027, 1, 1))
                .build();

        var response = UsedReagentBatchMapper.toResponse(entity);

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.reagentName()).isEqualTo("Anti-IgG");
        assertThat(response.lotNumber()).isEqualTo("LOT-001");
        assertThat(response.expiryDate()).isEqualTo(LocalDate.of(2027, 1, 1));
    }

    @Test
    void toResponseList_shouldMapMultipleEntities() {
        var reagent = ReagentCatalog.builder().id(1L).name("A").build();
        var entities = List.of(
                UsedReagentBatch.builder().id(1L).reagent(reagent).lotNumber("L1").build(),
                UsedReagentBatch.builder().id(2L).reagent(reagent).lotNumber("L2").build()
        );

        var responses = UsedReagentBatchMapper.toResponseList(entities);

        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).lotNumber()).isEqualTo("L1");
        assertThat(responses.get(1).lotNumber()).isEqualTo("L2");
    }
}
