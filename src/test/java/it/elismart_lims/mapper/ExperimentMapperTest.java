package it.elismart_lims.mapper;

import it.elismart_lims.dto.ExperimentRequest;
import it.elismart_lims.dto.UsedReagentBatchRequest;
import it.elismart_lims.model.CurveType;
import it.elismart_lims.model.Experiment;
import it.elismart_lims.model.ExperimentStatus;
import it.elismart_lims.model.Protocol;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ExperimentMapper}.
 */
class ExperimentMapperTest {

    @Test
    void toEntity_shouldMapRequestToEntity() {
        var protocol = Protocol.builder().id(1L).name("IgG Test").build();
        var request = new ExperimentRequest(
                "Run 2026-04-05",
                LocalDateTime.of(2026, 4, 5, 10, 0),
                1L,
                ExperimentStatus.OK,
                List.of(new UsedReagentBatchRequest(1L)),
                List.of());

        var entity = ExperimentMapper.toEntity(request, protocol);

        assertThat(entity.getName()).isEqualTo("Run 2026-04-05");
        assertThat(entity.getProtocol()).isEqualTo(protocol);
        assertThat(entity.getStatus()).isEqualTo(ExperimentStatus.OK);
    }

    @Test
    void toResponse_shouldMapEntityToResponse() {
        var protocol = Protocol.builder().name("IgG Test").curveType(CurveType.FOUR_PARAMETER_LOGISTIC).build();
        var entity = Experiment.builder()
                .id(1L)
                .name("Run 2026-04-05")
                .date(LocalDateTime.of(2026, 4, 5, 10, 0))
                .status(ExperimentStatus.OK)
                .protocol(protocol)
                .usedReagentBatches(List.of())
                .measurementPairs(List.of())
                .build();

        var response = ExperimentMapper.toResponse(entity);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.protocolName()).isEqualTo("IgG Test");
        assertThat(response.protocolCurveType()).isEqualTo(CurveType.FOUR_PARAMETER_LOGISTIC);
        assertThat(response.usedReagentBatches()).isEmpty();
        assertThat(response.measurementPairs()).isEmpty();
    }
}
