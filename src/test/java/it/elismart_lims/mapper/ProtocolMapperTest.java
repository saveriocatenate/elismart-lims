package it.elismart_lims.mapper;

import it.elismart_lims.dto.ProtocolRequest;
import it.elismart_lims.model.CurveType;
import it.elismart_lims.model.Protocol;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ProtocolMapper}.
 */
class ProtocolMapperTest {

    @Test
    void toEntity_shouldMapRequestToEntity() {
        var request = new ProtocolRequest("IgG Test", 7, 3, 15.0, 10.0, CurveType.FOUR_PARAMETER_LOGISTIC);

        var entity = ProtocolMapper.toEntity(request);

        assertThat(entity.getName()).isEqualTo("IgG Test");
        assertThat(entity.getNumCalibrationPairs()).isEqualTo(7);
        assertThat(entity.getNumControlPairs()).isEqualTo(3);
        assertThat(entity.getMaxCvAllowed()).isEqualTo(15.0);
        assertThat(entity.getMaxErrorAllowed()).isEqualTo(10.0);
        assertThat(entity.getCurveType()).isEqualTo(CurveType.FOUR_PARAMETER_LOGISTIC);
    }

    @Test
    void toResponse_shouldMapEntityToResponse() {
        var entity = Protocol.builder()
                .id(1L)
                .name("IgG Test")
                .numCalibrationPairs(7)
                .numControlPairs(3)
                .maxCvAllowed(15.0)
                .maxErrorAllowed(10.0)
                .curveType(CurveType.FOUR_PARAMETER_LOGISTIC)
                .build();

        var response = ProtocolMapper.toResponse(entity);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.name()).isEqualTo("IgG Test");
        assertThat(response.numCalibrationPairs()).isEqualTo(7);
        assertThat(response.curveType()).isEqualTo(CurveType.FOUR_PARAMETER_LOGISTIC);
    }
}
