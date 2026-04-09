package it.elismart_lims.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.elismart_lims.config.TestSecurityConfig;
import it.elismart_lims.dto.RoleUpdateRequest;
import it.elismart_lims.dto.UserResponse;
import it.elismart_lims.exception.model.ResourceNotFoundException;
import it.elismart_lims.model.UserRole;
import it.elismart_lims.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller tests for {@link UserController}.
 */
@Import(TestSecurityConfig.class)
@WebMvcTest(UserController.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UserService userService;

    private UserResponse analystResponse() {
        return UserResponse.builder()
                .id(1L)
                .username("analyst1")
                .role(UserRole.ANALYST)
                .enabled(true)
                .createdAt(LocalDateTime.now())
                .build();
    }

    // -------------------------------------------------------------------------
    // GET /api/users
    // -------------------------------------------------------------------------

    @Test
    void getAllUsers_shouldReturn200AndList() throws Exception {
        when(userService.getAllUsers()).thenReturn(List.of(analystResponse()));

        mockMvc.perform(get("/api/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].username").value("analyst1"))
                .andExpect(jsonPath("$[0].role").value("ANALYST"))
                .andExpect(jsonPath("$[0].enabled").value(true));
    }

    @Test
    void getAllUsers_shouldReturn200AndEmptyList_whenNoUsers() throws Exception {
        when(userService.getAllUsers()).thenReturn(List.of());

        mockMvc.perform(get("/api/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    // -------------------------------------------------------------------------
    // PUT /api/users/{id}/role
    // -------------------------------------------------------------------------

    @Test
    void updateRole_shouldReturn200AndUpdatedResponse() throws Exception {
        var updated = UserResponse.builder()
                .id(1L).username("analyst1").role(UserRole.REVIEWER)
                .enabled(true).createdAt(LocalDateTime.now()).build();
        when(userService.updateRole(eq(1L), eq(UserRole.REVIEWER))).thenReturn(updated);

        var request = new RoleUpdateRequest(UserRole.REVIEWER);
        mockMvc.perform(put("/api/users/1/role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("REVIEWER"));
    }

    @Test
    void updateRole_shouldReturn400_whenRoleIsNull() throws Exception {
        mockMvc.perform(put("/api/users/1/role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":null}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateRole_shouldReturn404_whenUserNotFound() throws Exception {
        when(userService.updateRole(eq(99L), any()))
                .thenThrow(new ResourceNotFoundException("User not found with id: 99"));

        var request = new RoleUpdateRequest(UserRole.ANALYST);
        mockMvc.perform(put("/api/users/99/role")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    // -------------------------------------------------------------------------
    // DELETE /api/users/{id}
    // -------------------------------------------------------------------------

    @Test
    void disableUser_shouldReturn200AndDisabledResponse() throws Exception {
        var disabled = UserResponse.builder()
                .id(1L).username("analyst1").role(UserRole.ANALYST)
                .enabled(false).createdAt(LocalDateTime.now()).build();
        when(userService.disableUser(1L)).thenReturn(disabled);

        mockMvc.perform(delete("/api/users/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));
    }

    @Test
    void disableUser_shouldReturn409_whenAdminDisablesSelf() throws Exception {
        when(userService.disableUser(2L))
                .thenThrow(new IllegalStateException("An admin cannot disable their own account."));

        mockMvc.perform(delete("/api/users/2"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void disableUser_shouldReturn404_whenUserNotFound() throws Exception {
        when(userService.disableUser(99L))
                .thenThrow(new ResourceNotFoundException("User not found with id: 99"));

        mockMvc.perform(delete("/api/users/99"))
                .andExpect(status().isNotFound());
    }
}
