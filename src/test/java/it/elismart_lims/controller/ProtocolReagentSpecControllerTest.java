package it.elismart_lims.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.elismart_lims.dto.ProtocolReagentSpecRequest;
import it.elismart_lims.dto.ProtocolReagentSpecResponse;
import it.elismart_lims.exception.model.ResourceNotFoundException;
import it.elismart_lims.service.ProtocolReagentSpecService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller tests for {@link ProtocolReagentSpecController}.
 */
@WebMvcTest(ProtocolReagentSpecController.class)
class ProtocolReagentSpecControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ProtocolReagentSpecService protocolReagentSpecService;

    private ProtocolReagentSpecResponse sampleResponse(Long id, String reagentName, boolean mandatory) {
        return ProtocolReagentSpecResponse.builder()
                .id(id)
                .protocolId(1L)
                .reagentId(10L)
                .reagentName(reagentName)
                .isMandatory(mandatory)
                .build();
    }

    @Test
    void getByProtocol_shouldReturnSpecList() throws Exception {
        when(protocolReagentSpecService.getByProtocolId(1L)).thenReturn(List.of(
                sampleResponse(100L, "Anti-IgG", true),
                sampleResponse(101L, "Buffer", false)
        ));

        mockMvc.perform(get("/api/protocol-reagent-specs").param("protocolId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].reagentName").value("Anti-IgG"))
                .andExpect(jsonPath("$[0].isMandatory").value(true))
                .andExpect(jsonPath("$[1].reagentName").value("Buffer"))
                .andExpect(jsonPath("$[1].isMandatory").value(false));
    }

    @Test
    void getByProtocol_shouldReturnEmptyList_whenNoSpecs() throws Exception {
        when(protocolReagentSpecService.getByProtocolId(99L)).thenReturn(List.of());

        mockMvc.perform(get("/api/protocol-reagent-specs").param("protocolId", "99"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void create_shouldReturn201() throws Exception {
        var request = new ProtocolReagentSpecRequest(1L, 10L, true);
        when(protocolReagentSpecService.create(any())).thenReturn(sampleResponse(100L, "Anti-IgG", true));

        mockMvc.perform(post("/api/protocol-reagent-specs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(100))
                .andExpect(jsonPath("$.reagentName").value("Anti-IgG"))
                .andExpect(jsonPath("$.isMandatory").value(true));
    }

    @Test
    void create_shouldReturn400_whenProtocolNotFound() throws Exception {
        var request = new ProtocolReagentSpecRequest(99L, 10L, true);
        when(protocolReagentSpecService.create(any()))
                .thenThrow(new ResourceNotFoundException("Protocol not found with id: 99"));

        mockMvc.perform(post("/api/protocol-reagent-specs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void create_shouldReturn400_whenInvalidRequest() throws Exception {
        // isMandatory is @NotNull — send null to trigger validation error
        mockMvc.perform(post("/api/protocol-reagent-specs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"protocolId\":1,\"reagentId\":10}"))
                .andExpect(status().isBadRequest());
    }
}
