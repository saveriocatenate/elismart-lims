package it.elismart_lims.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.elismart_lims.dto.ProtocolRequest;
import it.elismart_lims.dto.ProtocolResponse;
import it.elismart_lims.exception.model.ResourceNotFoundException;
import it.elismart_lims.service.ProtocolService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller tests for {@link ProtocolController}.
 */
@WebMvcTest(ProtocolController.class)
class ProtocolControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ProtocolService protocolService;

    @Test
    void getAll_shouldReturnProtocolList() throws Exception {
        var p1 = new ProtocolResponse(1L, "IgG Test", 7, 3, 15.0, 10.0);
        var p2 = new ProtocolResponse(2L, "IgM Test", 5, 2, 10.0, 8.0);
        when(protocolService.getAll()).thenReturn(List.of(p1, p2));

        mockMvc.perform(get("/api/protocols"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("IgG Test"))
                .andExpect(jsonPath("$[1].name").value("IgM Test"));
    }

    @Test
    void getAll_shouldReturnEmptyList_whenNoProtocols() throws Exception {
        when(protocolService.getAll()).thenReturn(List.of());

        mockMvc.perform(get("/api/protocols"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void getById_shouldReturnProtocol() throws Exception {
        var response = new ProtocolResponse(1L, "IgG Test", 7, 3, 15.0, 10.0);
        when(protocolService.getById(1L)).thenReturn(response);

        mockMvc.perform(get("/api/protocols/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("IgG Test"));
    }

    @Test
    void getById_shouldReturn404_whenNotFound() throws Exception {
        when(protocolService.getById(1L)).thenThrow(new ResourceNotFoundException("Protocol not found"));

        mockMvc.perform(get("/api/protocols/1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Protocol not found"));
    }

    @Test
    void create_shouldReturn201() throws Exception {
        var request = new ProtocolRequest("IgG Test", 7, 3, 15.0, 10.0);
        var response = new ProtocolResponse(1L, "IgG Test", 7, 3, 15.0, 10.0);
        when(protocolService.create(any())).thenReturn(response);

        mockMvc.perform(post("/api/protocols")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("IgG Test"));
    }

    @Test
    void create_shouldReturn400_whenInvalid() throws Exception {
        var request = new ProtocolRequest("", 7, 3, 15.0, 10.0);

        mockMvc.perform(post("/api/protocols")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void delete_shouldReturn204() throws Exception {
        mockMvc.perform(delete("/api/protocols/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void delete_shouldReturn404_whenNotFound() throws Exception {
        doThrow(new ResourceNotFoundException("Protocol not found")).when(protocolService).delete(1L);

        mockMvc.perform(delete("/api/protocols/1"))
                .andExpect(status().isNotFound());
    }
}
