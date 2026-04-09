package it.elismart_lims.controller;

import it.elismart_lims.model.UserRole;
import it.elismart_lims.security.JwtTokenProvider;
import it.elismart_lims.service.ProtocolService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security-layer integration tests verifying RBAC rules on
 * {@link ProtocolController} DELETE endpoints.
 *
 * <p>Uses the full Spring Boot context with a mocked {@link JwtTokenProvider}
 * so that fake Bearer tokens can be injected to simulate different roles
 * without generating real JWTs. The production
 * {@link it.elismart_lims.config.SecurityConfig} and {@link it.elismart_lims.security.JwtAuthFilter}
 * are exercised end-to-end.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProtocolControllerSecurityTest {

    private static final String ANALYST_TOKEN = "fake-analyst-token";
    private static final String ADMIN_TOKEN = "fake-admin-token";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private ProtocolService protocolService;

    @Test
    void delete_shouldReturn403_whenCalledByAnalyst() throws Exception {
        when(jwtTokenProvider.validateToken(ANALYST_TOKEN)).thenReturn(true);
        when(jwtTokenProvider.getUsernameFromToken(ANALYST_TOKEN)).thenReturn("analyst");
        when(jwtTokenProvider.getRoleFromToken(ANALYST_TOKEN)).thenReturn(UserRole.ANALYST);

        mockMvc.perform(delete("/api/protocols/1")
                        .header("Authorization", "Bearer " + ANALYST_TOKEN))
                .andExpect(status().isForbidden());
    }

    @Test
    void delete_shouldReturn204_whenCalledByAdmin() throws Exception {
        when(jwtTokenProvider.validateToken(ADMIN_TOKEN)).thenReturn(true);
        when(jwtTokenProvider.getUsernameFromToken(ADMIN_TOKEN)).thenReturn("admin");
        when(jwtTokenProvider.getRoleFromToken(ADMIN_TOKEN)).thenReturn(UserRole.ADMIN);
        doNothing().when(protocolService).delete(1L);

        mockMvc.perform(delete("/api/protocols/1")
                        .header("Authorization", "Bearer " + ADMIN_TOKEN))
                .andExpect(status().isNoContent());
    }
}
