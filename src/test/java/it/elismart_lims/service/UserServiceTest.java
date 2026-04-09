package it.elismart_lims.service;

import it.elismart_lims.dto.UserResponse;
import it.elismart_lims.exception.model.ResourceNotFoundException;
import it.elismart_lims.model.User;
import it.elismart_lims.model.UserRole;
import it.elismart_lims.repository.UserRepository;
import it.elismart_lims.service.audit.AuditLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link UserService}.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private UserService userService;

    private User analyst;
    private User admin;

    @BeforeEach
    void setUp() {
        analyst = User.builder()
                .id(1L)
                .username("analyst1")
                .password("hashed")
                .role(UserRole.ANALYST)
                .enabled(true)
                .build();

        admin = User.builder()
                .id(2L)
                .username("admin")
                .password("hashed")
                .role(UserRole.ADMIN)
                .enabled(true)
                .build();

        SecurityContextHolder.clearContext();
    }

    // -------------------------------------------------------------------------
    // getAllUsers
    // -------------------------------------------------------------------------

    @Test
    void getAllUsers_shouldReturnAllUserResponses() {
        when(userRepository.findAll()).thenReturn(List.of(analyst, admin));

        List<UserResponse> result = userService.getAllUsers();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).username()).isEqualTo("analyst1");
        assertThat(result.get(1).username()).isEqualTo("admin");
        verify(userRepository).findAll();
    }

    @Test
    void getAllUsers_shouldReturnEmptyList_whenNoUsersExist() {
        when(userRepository.findAll()).thenReturn(List.of());

        List<UserResponse> result = userService.getAllUsers();

        assertThat(result).isEmpty();
    }

    // -------------------------------------------------------------------------
    // updateRole
    // -------------------------------------------------------------------------

    @Test
    void updateRole_shouldChangeRoleAndAuditLog() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(analyst));
        when(userRepository.save(analyst)).thenReturn(analyst);

        UserResponse result = userService.updateRole(1L, UserRole.REVIEWER);

        assertThat(result.role()).isEqualTo(UserRole.REVIEWER);
        verify(auditLogService).logChange(
                eq("User"), eq(1L), eq("role"),
                eq("ANALYST"), eq("REVIEWER"), isNull());
        verify(userRepository).save(analyst);
    }

    @Test
    void updateRole_shouldThrow_whenUserNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.updateRole(99L, UserRole.REVIEWER))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    // -------------------------------------------------------------------------
    // disableUser
    // -------------------------------------------------------------------------

    @Test
    void disableUser_shouldSetEnabledFalseAndAuditLog() {
        setSecurityContext("admin");
        when(userRepository.findById(1L)).thenReturn(Optional.of(analyst));
        when(userRepository.save(analyst)).thenReturn(analyst);

        UserResponse result = userService.disableUser(1L);

        assertThat(result.enabled()).isFalse();
        verify(auditLogService).logChange(
                eq("User"), eq(1L), eq("enabled"),
                eq("true"), eq("false"), isNull());
        verify(userRepository).save(analyst);
    }

    @Test
    void disableUser_shouldThrow_whenAdminDisablesSelf() {
        setSecurityContext("admin");
        when(userRepository.findById(2L)).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> userService.disableUser(2L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cannot disable their own account");

        verify(userRepository, never()).save(any());
        verify(auditLogService, never()).logChange(any(), any(), any(), any(), any(), any());
    }

    @Test
    void disableUser_shouldThrow_whenUserNotFound() {
        setSecurityContext("admin");
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.disableUser(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void setSecurityContext(String username) {
        var auth = new UsernamePasswordAuthenticationToken(username, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
