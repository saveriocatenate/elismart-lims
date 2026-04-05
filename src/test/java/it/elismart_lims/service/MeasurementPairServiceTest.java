package it.elismart_lims.service;

import it.elismart_lims.model.MeasurementPair;
import it.elismart_lims.repository.MeasurementPairRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link MeasurementPairService}.
 */
@ExtendWith(MockitoExtension.class)
class MeasurementPairServiceTest {

    @Mock
    private MeasurementPairRepository measurementPairRepository;

    @InjectMocks
    private MeasurementPairService measurementPairService;

    private MeasurementPair pair;

    @BeforeEach
    void setUp() {
        pair = MeasurementPair.builder()
                .pairType("CALIBRATION")
                .signal1(0.45)
                .signal2(0.47)
                .cvPct(3.04)
                .recoveryPct(98.5)
                .isOutlier(false)
                .build();
    }

    @Test
    void saveAll_shouldPersistPairs() {
        List<MeasurementPair> pairs = List.of(pair);
        when(measurementPairRepository.saveAll(anyList())).thenReturn(pairs);

        var result = measurementPairService.saveAll(pairs);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getPairType()).isEqualTo("CALIBRATION");
        verify(measurementPairRepository, times(1)).saveAll(pairs);
    }
}
