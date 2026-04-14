package it.elismart_lims.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.elismart_lims.dto.MeasurementPairResponse;
import it.elismart_lims.dto.OutlierUpdateRequest;
import it.elismart_lims.exception.model.ResourceNotFoundException;
import it.elismart_lims.model.PairType;
import it.elismart_lims.service.MeasurementPairService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import it.elismart_lims.config.TestSecurityConfig;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller tests for {@link MeasurementPairController}.
 */
@Import(TestSecurityConfig.class)
@WebMvcTest(MeasurementPairController.class)
class MeasurementPairControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private MeasurementPairService measurementPairService;

    @Test
    void updateOutlier_shouldReturn200_withUpdatedValue() throws Exception {
        var response = MeasurementPairResponse.builder()
                .id(1L)
                .pairType(PairType.CONTROL)
                .signal1(0.45)
                .signal2(0.47)
                .signalMean(0.46)
                .cvPct(3.04)
                .isOutlier(true)
                .build();

        when(measurementPairService.updateOutlier(eq(1L), any(OutlierUpdateRequest.class)))
                .thenReturn(response);

        mockMvc.perform(patch("/api/measurement-pairs/1/outlier")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new OutlierUpdateRequest(true, "Instrument drift"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.isOutlier").value(true));
    }

    @Test
    void updateOutlier_shouldReturn404_whenPairNotFound() throws Exception {
        when(measurementPairService.updateOutlier(eq(99L), any(OutlierUpdateRequest.class)))
                .thenThrow(new ResourceNotFoundException("MeasurementPair not found with id: 99"));

        mockMvc.perform(patch("/api/measurement-pairs/99/outlier")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new OutlierUpdateRequest(false, null))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("MeasurementPair not found with id: 99"));
    }

    @Test
    void updateOutlier_shouldReturn400_whenBodyMissing() throws Exception {
        mockMvc.perform(patch("/api/measurement-pairs/1/outlier")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }
}
