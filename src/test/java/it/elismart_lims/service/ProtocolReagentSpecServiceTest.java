package it.elismart_lims.service;

import it.elismart_lims.dto.ProtocolReagentSpecRequest;
import it.elismart_lims.dto.ProtocolReagentSpecResponse;
import it.elismart_lims.exception.model.ResourceNotFoundException;
import it.elismart_lims.model.Protocol;
import it.elismart_lims.model.ProtocolReagentSpec;
import it.elismart_lims.model.ReagentCatalog;
import it.elismart_lims.repository.ProtocolReagentSpecRepository;
import it.elismart_lims.repository.ProtocolRepository;
import it.elismart_lims.repository.ReagentCatalogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ProtocolReagentSpecService}.
 */
@ExtendWith(MockitoExtension.class)
class ProtocolReagentSpecServiceTest {

    @Mock
    private ProtocolReagentSpecRepository protocolReagentSpecRepository;

    @Mock
    private ProtocolRepository protocolRepository;

    @Mock
    private ReagentCatalogRepository reagentCatalogRepository;

    @InjectMocks
    private ProtocolReagentSpecService protocolReagentSpecService;

    private Protocol protocol;
    private ReagentCatalog reagent1;
    private ReagentCatalog reagent2;
    private ReagentCatalog reagent3;

    @BeforeEach
    void setUp() {
        protocol = Protocol.builder()
                .id(1L)
                .name("ELISA Test")
                .build();

        reagent1 = ReagentCatalog.builder().id(10L).name("Anti-IgG").manufacturer("Sigma").build();
        reagent2 = ReagentCatalog.builder().id(20L).name("Anti-IgM").manufacturer("Sigma").build();
        reagent3 = ReagentCatalog.builder().id(30L).name("Buffer").manufacturer("Merck").build();
    }

    @Test
    void getMandatoryReagentIds_shouldReturnOnlyMandatoryReagentIds() {
        ProtocolReagentSpec spec1 = ProtocolReagentSpec.builder()
                .protocol(protocol).reagent(reagent1).isMandatory(true).build();
        ProtocolReagentSpec spec2 = ProtocolReagentSpec.builder()
                .protocol(protocol).reagent(reagent2).isMandatory(true).build();
        ProtocolReagentSpec spec3 = ProtocolReagentSpec.builder()
                .protocol(protocol).reagent(reagent3).isMandatory(false).build();

        when(protocolReagentSpecRepository.findByProtocolId(1L))
                .thenReturn(List.of(spec1, spec2, spec3));

        Set<Long> result = protocolReagentSpecService.getMandatoryReagentIds(1L);

        assertThat(result).containsExactlyInAnyOrder(10L, 20L).doesNotContain(30L);
    }

    @Test
    void getMandatoryReagentIds_shouldReturnEmpty_whenNoMandatoryReagents() {
        ProtocolReagentSpec spec = ProtocolReagentSpec.builder()
                .protocol(protocol).reagent(reagent3).isMandatory(false).build();

        when(protocolReagentSpecRepository.findByProtocolId(1L))
                .thenReturn(List.of(spec));

        Set<Long> result = protocolReagentSpecService.getMandatoryReagentIds(1L);

        assertThat(result).isEmpty();
    }

    @Test
    void getMandatoryReagentIds_shouldReturnEmpty_whenNoSpecs() {
        when(protocolReagentSpecRepository.findByProtocolId(1L))
                .thenReturn(List.of());

        Set<Long> result = protocolReagentSpecService.getMandatoryReagentIds(1L);

        assertThat(result).isEmpty();
    }

    @Test
    void getByProtocolId_shouldReturnDTOs_withReagentName() {
        ProtocolReagentSpec spec1 = ProtocolReagentSpec.builder()
                .id(100L).protocol(protocol).reagent(reagent1).isMandatory(true).build();
        ProtocolReagentSpec spec2 = ProtocolReagentSpec.builder()
                .id(101L).protocol(protocol).reagent(reagent3).isMandatory(false).build();

        when(protocolReagentSpecRepository.findByProtocolId(1L))
                .thenReturn(List.of(spec1, spec2));

        List<ProtocolReagentSpecResponse> result = protocolReagentSpecService.getByProtocolId(1L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).reagentName()).isEqualTo("Anti-IgG");
        assertThat(result.get(0).isMandatory()).isTrue();
        assertThat(result.get(1).reagentName()).isEqualTo("Buffer");
        assertThat(result.get(1).isMandatory()).isFalse();
    }

    @Test
    void getByProtocolId_shouldReturnEmpty_whenNoSpecs() {
        when(protocolReagentSpecRepository.findByProtocolId(1L)).thenReturn(List.of());

        List<ProtocolReagentSpecResponse> result = protocolReagentSpecService.getByProtocolId(1L);

        assertThat(result).isEmpty();
    }

    @Test
    void create_shouldReturnDTO_whenProtocolAndReagentExist() {
        ProtocolReagentSpecRequest request = new ProtocolReagentSpecRequest(1L, 10L, true);
        ProtocolReagentSpec saved = ProtocolReagentSpec.builder()
                .id(200L).protocol(protocol).reagent(reagent1).isMandatory(true).build();

        when(protocolRepository.findById(1L)).thenReturn(Optional.of(protocol));
        when(reagentCatalogRepository.findById(10L)).thenReturn(Optional.of(reagent1));
        when(protocolReagentSpecRepository.save(any(ProtocolReagentSpec.class))).thenReturn(saved);

        ProtocolReagentSpecResponse result = protocolReagentSpecService.create(request);

        assertThat(result.id()).isEqualTo(200L);
        assertThat(result.protocolId()).isEqualTo(1L);
        assertThat(result.reagentId()).isEqualTo(10L);
        assertThat(result.reagentName()).isEqualTo("Anti-IgG");
        assertThat(result.isMandatory()).isTrue();
        verify(protocolReagentSpecRepository).save(any(ProtocolReagentSpec.class));
    }

    @Test
    void create_shouldThrow_whenProtocolNotFound() {
        ProtocolReagentSpecRequest request = new ProtocolReagentSpecRequest(99L, 10L, true);
        when(protocolRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> protocolReagentSpecService.create(request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Protocol not found with id: 99");
    }

    @Test
    void create_shouldThrow_whenReagentNotFound() {
        ProtocolReagentSpecRequest request = new ProtocolReagentSpecRequest(1L, 99L, true);
        when(protocolRepository.findById(1L)).thenReturn(Optional.of(protocol));
        when(reagentCatalogRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> protocolReagentSpecService.create(request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Reagent catalog not found with id: 99");
    }
}
