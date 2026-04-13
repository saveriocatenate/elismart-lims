package it.elismart_lims.mapper;

import it.elismart_lims.model.ReagentBatch;
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

    private final ReagentCatalog reagent = ReagentCatalog.builder()
            .id(1L).name("Anti-IgG").manufacturer("Sigma").build();

    private final ReagentBatch batch = ReagentBatch.builder()
            .id(5L)
            .reagent(reagent)
            .lotNumber("LOT-001")
            .expiryDate(LocalDate.of(2027, 1, 1))
            .build();

    @Test
    void toResponse_shouldMapEntityToResponse() {
        var entity = UsedReagentBatch.builder()
                .id(10L)
                .reagent(reagent)
                .reagentBatch(batch)
                .build();

        var response = UsedReagentBatchMapper.toResponse(entity);

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.reagentBatch()).isNotNull();
        assertThat(response.reagentBatch().id()).isEqualTo(5L);
        assertThat(response.reagentBatch().lotNumber()).isEqualTo("LOT-001");
        assertThat(response.reagentBatch().expiryDate()).isEqualTo(LocalDate.of(2027, 1, 1));
        assertThat(response.reagentBatch().reagentName()).isEqualTo("Anti-IgG");
        assertThat(response.reagentBatch().manufacturer()).isEqualTo("Sigma");
    }

    @Test
    void toResponseList_shouldMapMultipleEntities() {
        var batch1 = ReagentBatch.builder().id(1L).reagent(reagent).lotNumber("L1").expiryDate(LocalDate.now()).build();
        var batch2 = ReagentBatch.builder().id(2L).reagent(reagent).lotNumber("L2").expiryDate(LocalDate.now()).build();
        var entities = List.of(
                UsedReagentBatch.builder().id(10L).reagent(reagent).reagentBatch(batch1).build(),
                UsedReagentBatch.builder().id(11L).reagent(reagent).reagentBatch(batch2).build()
        );

        var responses = UsedReagentBatchMapper.toResponseList(entities);

        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).reagentBatch().lotNumber()).isEqualTo("L1");
        assertThat(responses.get(1).reagentBatch().lotNumber()).isEqualTo("L2");
    }
}
