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
     *
     * @param experiment the Experiment entity
     * @return the response DTO with nested collections
     */
    public static ExperimentResponse toResponse(Experiment experiment) {
        return ExperimentResponse.builder()
                .withId(experiment.getId())
                .withName(experiment.getName())
                .withDate(experiment.getDate())
                .withStatus(experiment.getStatus())
                .withProtocolName(experiment.getProtocol().getName())
                .withUsedReagentBatches(UsedReagentBatchMapper.toResponseList(experiment.getUsedReagentBatches()))
                .withMeasurementPairs(MeasurementPairMapper.toResponseList(experiment.getMeasurementPairs()))
                .build();
    }
}
