package it.elismart_lims.mapper;

import it.elismart_lims.dto.ProtocolRequest;
import it.elismart_lims.dto.ProtocolResponse;
import it.elismart_lims.model.Protocol;

/**
 * Static mapper between Protocol entities and their DTOs.
 */
public final class ProtocolMapper {

    private ProtocolMapper() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Converts a ProtocolRequest DTO into a Protocol entity.
     *
     * @param request the request payload
     * @return the built Protocol entity
     */
    public static Protocol toEntity(ProtocolRequest request) {
        return Protocol.builder()
                .name(request.name())
                .numCalibrationPairs(request.numCalibrationPairs())
                .numControlPairs(request.numControlPairs())
                .maxCvAllowed(request.maxCvAllowed())
                .maxErrorAllowed(request.maxErrorAllowed())
                .build();
    }

    /**
     * Converts a Protocol entity into a ProtocolResponse DTO.
     *
     * @param entity the Protocol entity
     * @return the response DTO
     */
    public static ProtocolResponse toResponse(Protocol entity) {
        return ProtocolResponse.builder()
                .id(entity.getId())
                .name(entity.getName())
                .numCalibrationPairs(entity.getNumCalibrationPairs())
                .numControlPairs(entity.getNumControlPairs())
                .maxCvAllowed(entity.getMaxCvAllowed())
                .maxErrorAllowed(entity.getMaxErrorAllowed())
                .build();
    }
}
