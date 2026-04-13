package it.elismart_lims.mapper;

import it.elismart_lims.dto.ReagentBatchCreateRequest;
import it.elismart_lims.model.ReagentBatch;
import it.elismart_lims.model.ReagentCatalog;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ReagentBatchMapper}.
 */
class ReagentBatchMapperTest {

    private final ReagentCatalog reagent = ReagentCatalog.builder()
            .id(10L)
            .name("Anti-IgG")
            .manufacturer("Sigma")
            .build();

    @Test
    void toEntity_shouldMapRequestAndReagentToEntity() {
        var request = new ReagentBatchCreateRequest(
                10L, "LOT-001", LocalDate.of(2026, 12, 31), "SupplierX", "Keep refrigerated");

        ReagentBatch entity = ReagentBatchMapper.toEntity(request, reagent);

        assertThat(entity.getReagent()).isSameAs(reagent);
        assertThat(entity.getLotNumber()).isEqualTo("LOT-001");
        assertThat(entity.getExpiryDate()).isEqualTo(LocalDate.of(2026, 12, 31));
        assertThat(entity.getSupplier()).isEqualTo("SupplierX");
        assertThat(entity.getNotes()).isEqualTo("Keep refrigerated");
    }

    @Test
    void toEntity_shouldHandleNullOptionalFields() {
        var request = new ReagentBatchCreateRequest(10L, "LOT-002", LocalDate.of(2027, 6, 1), null, null);

        ReagentBatch entity = ReagentBatchMapper.toEntity(request, reagent);

        assertThat(entity.getSupplier()).isNull();
        assertThat(entity.getNotes()).isNull();
    }

    @Test
    void toResponse_shouldMapEntityToResponse() {
        ReagentBatch entity = ReagentBatch.builder()
                .id(1L)
                .reagent(reagent)
                .lotNumber("LOT-001")
                .expiryDate(LocalDate.of(2026, 12, 31))
                .supplier("SupplierX")
                .notes("Keep refrigerated")
                .build();

        var response = ReagentBatchMapper.toResponse(entity);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.reagentId()).isEqualTo(10L);
        assertThat(response.lotNumber()).isEqualTo("LOT-001");
        assertThat(response.expiryDate()).isEqualTo(LocalDate.of(2026, 12, 31));
        assertThat(response.supplier()).isEqualTo("SupplierX");
        assertThat(response.notes()).isEqualTo("Keep refrigerated");
    }

    @Test
    void toResponseList_shouldMapMultipleEntities() {
        var batch1 = ReagentBatch.builder().id(1L).reagent(reagent).lotNumber("A").expiryDate(LocalDate.now()).build();
        var batch2 = ReagentBatch.builder().id(2L).reagent(reagent).lotNumber("B").expiryDate(LocalDate.now()).build();

        var responses = ReagentBatchMapper.toResponseList(List.of(batch1, batch2));

        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).lotNumber()).isEqualTo("A");
        assertThat(responses.get(1).lotNumber()).isEqualTo("B");
    }
}
