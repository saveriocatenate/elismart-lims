package it.elismart_lims.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.elismart_lims.config.TestSecurityConfig;
import it.elismart_lims.dto.LoginRequest;
import it.elismart_lims.dto.LoginResponse;
import it.elismart_lims.dto.RegisterRequest;
import it.elismart_lims.dto.UserResponse;
import it.elismart_lims.model.UserRole;
import it.elismart_lims.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller tests for {@link AuthController}.
 */
@Import(TestSecurityConfig.class)
@WebMvcTest(AuthController.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuthService authService;

    @Test
    void login_shouldReturn200AndToken_whenCredentialsAreValid() throws Exception {
        var response = LoginResponse.builder()
                .token("signed.jwt.token")
                .username("admin")
                .role("ADMIN")
                .build();
        when(authService.login(any())).thenReturn(response);

        var request = new LoginRequest("admin", "admin");
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("signed.jwt.token"))
                .andExpect(jsonPath("$.username").value("admin"))
                .andExpect(jsonPath("$.role").value("ADMIN"));
    }

    @Test
    void login_shouldReturn401_whenCredentialsAreInvalid() throws Exception {
        when(authService.login(any())).thenThrow(new AuthenticationException("Bad credentials") {
        });

        var request = new LoginRequest("admin", "wrong-password");
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Invalid username or password."));
    }

    @Test
    void login_shouldReturn400_whenBodyIsEmpty() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_shouldReturn201AndUserResponse_whenRequestIsValid() throws Exception {
        var response = UserResponse.builder()
                .id(2L)
                .username("newanalyst")
                .role(UserRole.ANALYST)
                .enabled(true)
                .createdAt(LocalDateTime.now())
                .build();
        when(authService.register(any())).thenReturn(response);

        var request = new RegisterRequest("newanalyst", "secret123", UserRole.ANALYST);
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.username").value("newanalyst"))
                .andExpect(jsonPath("$.role").value("ANALYST"))
                .andExpect(jsonPath("$.enabled").value(true));
    }

    @Test
    void register_shouldReturn409_whenUsernameAlreadyExists() throws Exception {
        when(authService.register(any()))
                .thenThrow(new IllegalStateException("Username already exists: admin"));

        var request = new RegisterRequest("admin", "secret123", UserRole.ANALYST);
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Username already exists: admin"));
    }

    @Test
    void register_shouldReturn400_whenBodyIsEmpty() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
