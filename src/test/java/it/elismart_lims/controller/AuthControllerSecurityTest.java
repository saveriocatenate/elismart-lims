package it.elismart_lims.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.elismart_lims.dto.RegisterRequest;
import it.elismart_lims.model.UserRole;
import it.elismart_lims.security.JwtTokenProvider;
import it.elismart_lims.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security-layer integration tests verifying that {@code POST /api/auth/register}
 * is restricted to the {@code ADMIN} role.
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
class AuthControllerSecurityTest {

    private static final String ANALYST_TOKEN = "fake-analyst-token";
    private static final String REVIEWER_TOKEN = "fake-reviewer-token";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private AuthService authService;

    @Test
    void register_shouldReturn403_whenCalledByAnalyst() throws Exception {
        when(jwtTokenProvider.validateToken(ANALYST_TOKEN)).thenReturn(true);
        when(jwtTokenProvider.getUsernameFromToken(ANALYST_TOKEN)).thenReturn("analyst");
        when(jwtTokenProvider.getRoleFromToken(ANALYST_TOKEN)).thenReturn(UserRole.ANALYST);

        var request = new RegisterRequest("newuser", "password123", UserRole.ANALYST);
        mockMvc.perform(post("/api/auth/register")
                        .header("Authorization", "Bearer " + ANALYST_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void register_shouldReturn403_whenCalledByReviewer() throws Exception {
        when(jwtTokenProvider.validateToken(REVIEWER_TOKEN)).thenReturn(true);
        when(jwtTokenProvider.getUsernameFromToken(REVIEWER_TOKEN)).thenReturn("reviewer");
        when(jwtTokenProvider.getRoleFromToken(REVIEWER_TOKEN)).thenReturn(UserRole.REVIEWER);

        var request = new RegisterRequest("newuser", "password123", UserRole.ANALYST);
        mockMvc.perform(post("/api/auth/register")
                        .header("Authorization", "Bearer " + REVIEWER_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }
}
