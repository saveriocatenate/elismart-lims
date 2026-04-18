package it.elismart_lims.exception;

import it.elismart_lims.controller.ProtocolController;
import it.elismart_lims.dto.ProtocolRequest;
import it.elismart_lims.exception.model.ProtocolMismatchException;
import it.elismart_lims.exception.model.ResourceNotFoundException;
import it.elismart_lims.service.ProtocolService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import it.elismart_lims.config.TestSecurityConfig;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for {@link GlobalExceptionHandler}.
 * Tests the error response format by triggering exceptions through controller endpoints.
 */
@Import(TestSecurityConfig.class)
@WebMvcTest(ProtocolController.class)
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ProtocolService protocolService;

    @Test
    void resourceNotFoundException_shouldReturn404WithJsonBody() throws Exception {
        when(protocolService.getById(1L)).thenThrow(new ResourceNotFoundException("Protocol not found"));

        mockMvc.perform(get("/api/protocols/1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Protocol not found"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void protocolMismatchException_shouldReturn400WithJsonBody() throws Exception {
        when(protocolService.create(any())).thenThrow(new ProtocolMismatchException("Missing reagents"));

        var request = new ProtocolRequest("Test", 7, 3, 15.0, 10.0, it.elismart_lims.model.CurveType.FOUR_PARAMETER_LOGISTIC, "ng/mL");

        mockMvc.perform(post("/api/protocols")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Missing reagents"))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void validationError_shouldReturn400WithFieldErrors() throws Exception {
        var request = new ProtocolRequest("", 7, 3, 15.0, 10.0, it.elismart_lims.model.CurveType.FOUR_PARAMETER_LOGISTIC, "ng/mL");

        mockMvc.perform(post("/api/protocols")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void malformedJson_shouldReturn400WithJsonBody() throws Exception {
        mockMvc.perform(post("/api/protocols")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{invalid json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void wrongPathParamType_shouldReturn400WithJsonBody() throws Exception {
        mockMvc.perform(get("/api/protocols/not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void dataIntegrityViolation_shouldReturn409WithJsonBody() throws Exception {
        when(protocolService.create(any())).thenThrow(
                new DataIntegrityViolationException("Unique index or primary key violation"));

        var request = new ProtocolRequest("Test", 7, 3, 15.0, 10.0, it.elismart_lims.model.CurveType.FOUR_PARAMETER_LOGISTIC, "ng/mL");

        mockMvc.perform(post("/api/protocols")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.timestamp").exists());
    }
}
