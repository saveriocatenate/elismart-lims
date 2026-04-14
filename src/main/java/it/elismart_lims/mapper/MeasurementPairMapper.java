package it.elismart_lims.mapper;

import it.elismart_lims.dto.MeasurementPairRequest;
import it.elismart_lims.dto.MeasurementPairResponse;
import it.elismart_lims.model.Experiment;
import it.elismart_lims.model.MeasurementPair;
import it.elismart_lims.service.validation.ValidationConstants;
import it.elismart_lims.dto.SampleResponse;

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
     * <p>{@code signalMean} and {@code cvPct} are always computed server-side from the raw signals
     * using {@link ValidationConstants}. Any client-supplied values for these derived fields are
     * ignored per the server-side derivation policy.</p>
     *
     * @param request    the request payload
     * @param experiment the experiment to associate with
     * @return the built MeasurementPair entity
     */
    public static MeasurementPair toEntity(MeasurementPairRequest request, Experiment experiment) {
        double s1 = request.signal1();
        double s2 = request.signal2();
        return MeasurementPair.builder()
                .pairType(request.pairType())
                .concentrationNominal(request.concentrationNominal())
                .signal1(s1)
                .signal2(s2)
                .signalMean(ValidationConstants.calculateSignalMean(s1, s2))  // always server-side
                .cvPct(ValidationConstants.calculateCvPercent(s1, s2))        // always server-side
                .recoveryPct(null)                                             // always server-side; client-supplied value ignored
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
        SampleResponse sampleResponse = entity.getSample() != null
                ? SampleMapper.toResponse(entity.getSample())
                : null;
        return MeasurementPairResponse.builder()
                .id(entity.getId())
                .pairType(entity.getPairType())
                .concentrationNominal(entity.getConcentrationNominal())
                .signal1(entity.getSignal1())
                .signal2(entity.getSignal2())
                .signalMean(entity.getSignalMean())
                .cvPct(entity.getCvPct())
                .recoveryPct(entity.getRecoveryPct())
                .isOutlier(entity.getIsOutlier() != null && entity.getIsOutlier())
                .sample(sampleResponse)
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
