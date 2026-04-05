package it.elismart_lims.service;

import it.elismart_lims.model.Protocol;
import it.elismart_lims.model.ProtocolReagentSpec;
import it.elismart_lims.model.ReagentCatalog;
import it.elismart_lims.repository.ProtocolReagentSpecRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ProtocolReagentSpecService}.
 */
@ExtendWith(MockitoExtension.class)
class ProtocolReagentSpecServiceTest {

    @Mock
    private ProtocolReagentSpecRepository protocolReagentSpecRepository;

    @InjectMocks
    private ProtocolReagentSpecService protocolReagentSpecService;

    private Protocol protocol;

    @BeforeEach
    void setUp() {
        protocol = Protocol.builder()
                .id(1L)
                .name("ELISA Test")
                .build();
    }

    @Test
    void getMandatoryReagentIds_shouldReturnOnlyMandatoryReagentIds() {
        ReagentCatalog reagent1 = ReagentCatalog.builder().id(10L).name("Anti-IgG").build();
        ReagentCatalog reagent2 = ReagentCatalog.builder().id(20L).name("Anti-IgM").build();
        ReagentCatalog reagent3 = ReagentCatalog.builder().id(30L).name("Buffer").build();

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
        ReagentCatalog reagent = ReagentCatalog.builder().id(10L).name("Buffer").build();
        ProtocolReagentSpec spec = ProtocolReagentSpec.builder()
                .protocol(protocol).reagent(reagent).isMandatory(false).build();

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
}
