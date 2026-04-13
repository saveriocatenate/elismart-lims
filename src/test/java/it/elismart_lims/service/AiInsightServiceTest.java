package it.elismart_lims.service;

import it.elismart_lims.dto.AiInsightResponse;
import it.elismart_lims.mapper.AiInsightMapper;
import it.elismart_lims.model.AiInsight;
import it.elismart_lims.model.Experiment;
import it.elismart_lims.repository.AiInsightRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AiInsightService}.
 */
@ExtendWith(MockitoExtension.class)
class AiInsightServiceTest {

    @Mock
    private AiInsightRepository aiInsightRepository;

    @Mock
    private EntityManager entityManager;

    private AiInsightService aiInsightService;

    @BeforeEach
    void setUp() {
        aiInsightService = new AiInsightService(aiInsightRepository);
        ReflectionTestUtils.setField(aiInsightService, "entityManager", entityManager);
    }

    /**
     * save() persists an AiInsight with correct fields and returns a populated response DTO.
     */
    @Test
    void save_persistsInsightAndReturnsResponse() {
        // given
        Experiment expProxy = new Experiment();
        expProxy.setId(1L);
        when(entityManager.getReference(Experiment.class, 1L)).thenReturn(expProxy);

        AiInsight saved = AiInsight.builder()
                .id(10L)
                .userQuestion("Why did the control fail?")
                .aiResponse("The control failed due to reagent degradation.")
                .generatedAt(LocalDateTime.of(2026, 4, 13, 10, 0))
                .generatedBy("analyst1")
                .experiments(List.of(expProxy))
                .build();
        when(aiInsightRepository.save(any(AiInsight.class))).thenReturn(saved);

        // when
        AiInsightResponse response = aiInsightService.save(
                "Why did the control fail?",
                "The control failed due to reagent degradation.",
                "analyst1",
                List.of(1L)
        );

        // then
        ArgumentCaptor<AiInsight> captor = ArgumentCaptor.forClass(AiInsight.class);
        verify(aiInsightRepository).save(captor.capture());

        AiInsight captured = captor.getValue();
        assertThat(captured.getUserQuestion()).isEqualTo("Why did the control fail?");
        assertThat(captured.getAiResponse()).isEqualTo("The control failed due to reagent degradation.");
        assertThat(captured.getGeneratedBy()).isEqualTo("analyst1");
        assertThat(captured.getGeneratedAt()).isNotNull();
        assertThat(captured.getExperiments()).containsExactly(expProxy);

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.userQuestion()).isEqualTo("Why did the control fail?");
        assertThat(response.aiResponse()).isEqualTo("The control failed due to reagent degradation.");
        assertThat(response.generatedBy()).isEqualTo("analyst1");
        assertThat(response.experimentIds()).containsExactly(1L);
    }

    /**
     * save() creates entity proxy references for each experiment ID via EntityManager.getReference.
     */
    @Test
    void save_usesEntityManagerGetReference_forEachExperimentId() {
        // given
        Experiment proxy1 = new Experiment(); proxy1.setId(1L);
        Experiment proxy2 = new Experiment(); proxy2.setId(2L);
        when(entityManager.getReference(Experiment.class, 1L)).thenReturn(proxy1);
        when(entityManager.getReference(Experiment.class, 2L)).thenReturn(proxy2);

        AiInsight saved = AiInsight.builder()
                .id(20L)
                .userQuestion("Q")
                .aiResponse("A")
                .generatedAt(LocalDateTime.now())
                .generatedBy("user")
                .experiments(List.of(proxy1, proxy2))
                .build();
        when(aiInsightRepository.save(any())).thenReturn(saved);

        // when
        aiInsightService.save("Q", "A", "user", List.of(1L, 2L));

        // then
        verify(entityManager).getReference(eq(Experiment.class), eq(1L));
        verify(entityManager).getReference(eq(Experiment.class), eq(2L));
    }

    /**
     * getByExperimentId() returns insights from the repository mapped to response DTOs,
     * preserving the ordering returned by the repository.
     */
    @Test
    void getByExperimentId_returnsMappedInsightList() {
        // given
        Experiment exp = new Experiment(); exp.setId(5L);

        AiInsight insight1 = AiInsight.builder()
                .id(1L).userQuestion("Q1").aiResponse("A1")
                .generatedAt(LocalDateTime.of(2026, 4, 13, 12, 0))
                .generatedBy("user").experiments(List.of(exp)).build();

        AiInsight insight2 = AiInsight.builder()
                .id(2L).userQuestion("Q2").aiResponse("A2")
                .generatedAt(LocalDateTime.of(2026, 4, 12, 9, 0))
                .generatedBy("user").experiments(List.of(exp)).build();

        when(aiInsightRepository.findByExperimentsIdOrderByGeneratedAtDesc(5L))
                .thenReturn(List.of(insight1, insight2));

        // when
        List<AiInsightResponse> result = aiInsightService.getByExperimentId(5L);

        // then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).id()).isEqualTo(1L);
        assertThat(result.get(0).userQuestion()).isEqualTo("Q1");
        assertThat(result.get(1).id()).isEqualTo(2L);
        assertThat(result.get(1).userQuestion()).isEqualTo("Q2");
    }

    /**
     * getByExperimentId() returns an empty list when no insights exist for the given experiment.
     */
    @Test
    void getByExperimentId_returnsEmptyList_whenNoInsightsExist() {
        when(aiInsightRepository.findByExperimentsIdOrderByGeneratedAtDesc(99L))
                .thenReturn(List.of());

        List<AiInsightResponse> result = aiInsightService.getByExperimentId(99L);

        assertThat(result).isEmpty();
    }
}
