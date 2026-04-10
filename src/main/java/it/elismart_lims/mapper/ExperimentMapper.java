package it.elismart_lims.mapper;

import it.elismart_lims.dto.ExperimentRequest;
import it.elismart_lims.dto.ExperimentResponse;
import it.elismart_lims.model.Experiment;
import it.elismart_lims.model.Protocol;

/**
 * Static mapper between Experiment entities and their DTOs.
 */
public final class ExperimentMapper {

    private ExperimentMapper() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Converts an ExperimentRequest DTO into an Experiment entity.
     *
     * @param request  the request payload
     * @param protocol the associated Protocol entity
     * @return the built Experiment entity
     */
    public static Experiment toEntity(ExperimentRequest request, Protocol protocol) {
        return Experiment.builder()
                .name(request.name())
                .date(request.date())
                .status(request.status())
                .protocol(protocol)
                .build();
    }

    /**
     * Converts an Experiment entity into an ExperimentResponse DTO.
     * Audit fields ({@code createdAt}, {@code updatedAt}, {@code createdBy},
     * {@code updatedBy}) are read from the {@link it.elismart_lims.model.Auditable}
     * superclass.
     *
     * @param experiment the Experiment entity
     * @return the response DTO with nested collections and audit metadata
     */
    public static ExperimentResponse toResponse(Experiment experiment) {
        return ExperimentResponse.builder()
                .id(experiment.getId())
                .name(experiment.getName())
                .date(experiment.getDate())
                .status(experiment.getStatus())
                .protocolName(experiment.getProtocol().getName())
                .protocolCurveType(experiment.getProtocol().getCurveType())
                .curveParameters(experiment.getCurveParameters())
                .createdAt(experiment.getCreatedAt())
                .updatedAt(experiment.getUpdatedAt())
                .createdBy(experiment.getCreatedBy())
                .updatedBy(experiment.getUpdatedBy())
                .usedReagentBatches(UsedReagentBatchMapper.toResponseList(experiment.getUsedReagentBatches()))
                .measurementPairs(MeasurementPairMapper.toResponseList(experiment.getMeasurementPairs()))
                .build();
    }
}
