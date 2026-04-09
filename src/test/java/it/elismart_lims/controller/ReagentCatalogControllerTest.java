package it.elismart_lims.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.elismart_lims.dto.ReagentCatalogRequest;
import it.elismart_lims.dto.ReagentCatalogResponse;
import it.elismart_lims.exception.model.ResourceNotFoundException;
import it.elismart_lims.model.ReagentCatalog;
import it.elismart_lims.service.ReagentCatalogService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import it.elismart_lims.config.TestSecurityConfig;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller tests for {@link ReagentCatalogController}.
 */
@Import(TestSecurityConfig.class)
@WebMvcTest(ReagentCatalogController.class)
class ReagentCatalogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ReagentCatalogService reagentCatalogService;

    @Test
    void getAll_shouldReturnPagedReagents() throws Exception {
        var response = new ReagentCatalogResponse(1L, "Anti-IgG", "Sigma", "Goat anti-human IgG");
        when(reagentCatalogService.getAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(response)));

        mockMvc.perform(get("/api/reagent-catalogs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Anti-IgG"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void getById_shouldReturnReagent() throws Exception {
        var response = new ReagentCatalogResponse(1L, "Anti-IgG", "Sigma", "Desc");
        when(reagentCatalogService.getById(1L)).thenReturn(response);

        mockMvc.perform(get("/api/reagent-catalogs/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void getById_shouldReturn404_whenNotFound() throws Exception {
        when(reagentCatalogService.getById(1L)).thenThrow(new ResourceNotFoundException("Not found"));

        mockMvc.perform(get("/api/reagent-catalogs/1"))
                .andExpect(status().isNotFound());
    }

    @Test
    void create_shouldReturn201() throws Exception {
        var request = new ReagentCatalogRequest("Anti-IgG", "Sigma", "Desc");
        var entity = ReagentCatalog.builder().id(1L).name("Anti-IgG").manufacturer("Sigma").description("Desc").build();
        when(reagentCatalogService.create(any())).thenReturn(entity);

        mockMvc.perform(post("/api/reagent-catalogs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void delete_shouldReturn204() throws Exception {
        mockMvc.perform(delete("/api/reagent-catalogs/1"))
                .andExpect(status().isNoContent());
        verify(reagentCatalogService).delete(1L);
    }

    @Test
    void delete_shouldReturn404_whenNotFound() throws Exception {
        doThrow(new ResourceNotFoundException("Not found")).when(reagentCatalogService).delete(1L);

        mockMvc.perform(delete("/api/reagent-catalogs/1"))
                .andExpect(status().isNotFound());
    }
}
