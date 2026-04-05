package it.elismart_lims.mapper;

import it.elismart_lims.dto.MeasurementPairRequest;
import it.elismart_lims.dto.MeasurementPairResponse;
import it.elismart_lims.model.Experiment;
import it.elismart_lims.model.MeasurementPair;

import java.util.List;

/**
 * Static mapper between MeasurementPair entities and their DTOs.
 */
public final class MeasurementPairMapper {

    private MeasurementPairMapper() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Converts a MeasurementPairRequest DTO into a MeasurementPair entity.
     *
     * @param request the request payload
     * @return the built MeasurementPair entity
     */
    public static MeasurementPair toEntity(MeasurementPairRequest request) {
        return toEntity(request, null);
    }

    /**
     * Converts a MeasurementPairRequest DTO into a MeasurementPair entity linked to an experiment.
     *
     * @param request    the request payload
     * @param experiment the experiment to associate with
     * @return the built MeasurementPair entity
     */
    public static MeasurementPair toEntity(MeasurementPairRequest request, Experiment experiment) {
        return MeasurementPair.builder()
                .pairType(request.pairType())
                .concentrationNominal(request.concentrationNominal())
                .signal1(request.signal1())
                .signal2(request.signal2())
                .signalMean(request.signalMean())
                .cvPct(request.cvPct())
                .recoveryPct(request.recoveryPct())
                .experiment(experiment)
                .isOutlier(request.isOutlier() != null ? request.isOutlier() : false)
                .build();
    }

    /**
     * Converts a list of MeasurementPairRequest DTOs into entities linked to an experiment.
     *
     * @param requests   the list of request payloads
     * @param experiment the experiment to associate with
     * @return the list of built MeasurementPair entities
     */
    public static List<MeasurementPair> toEntityList(List<MeasurementPairRequest> requests, Experiment experiment) {
        return requests.stream()
                .map(req -> toEntity(req, experiment))
                .toList();
    }

    /**
     * Converts a MeasurementPair entity into a MeasurementPairResponse DTO.
     *
     * @param entity the MeasurementPair entity
     * @return the response DTO
     */
    public static MeasurementPairResponse toResponse(MeasurementPair entity) {
        return MeasurementPairResponse.builder()
                .withId(entity.getId())
                .withPairType(entity.getPairType())
                .withConcentrationNominal(entity.getConcentrationNominal())
                .withSignal1(entity.getSignal1())
                .withSignal2(entity.getSignal2())
                .withSignalMean(entity.getSignalMean())
                .withCvPct(entity.getCvPct())
                .withRecoveryPct(entity.getRecoveryPct())
                .withIsOutlier(entity.getIsOutlier() != null && entity.getIsOutlier())
                .build();
    }

    /**
     * Converts a list of MeasurementPair entities into response DTOs.
     *
     * @param entities the list of MeasurementPair entities
     * @return the list of response DTOs
     */
    public static List<MeasurementPairResponse> toResponseList(List<MeasurementPair> entities) {
        return entities.stream().map(MeasurementPairMapper::toResponse).toList();
    }
}
