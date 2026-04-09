package it.elismart_lims.service;

import it.elismart_lims.dto.UserResponse;
import it.elismart_lims.exception.model.ResourceNotFoundException;
import it.elismart_lims.mapper.UserMapper;
import it.elismart_lims.model.User;
import it.elismart_lims.model.UserRole;
import it.elismart_lims.repository.UserRepository;
import it.elismart_lims.service.audit.AuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Business logic for user management operations.
 *
 * <p>All public methods return DTOs — entities are never exposed to the controller.
 * Every mutation is recorded in the {@code audit_log} table via {@link AuditLogService}.</p>
 *
 * <p>Design constraints enforced here:
 * <ul>
 *   <li>An admin cannot disable their own account.</li>
 *   <li>Soft-delete only: {@code DELETE /api/users/{id}} sets {@code enabled = false}.</li>
 * </ul>
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    /**
     * Returns all users in the system, ordered by ID ascending.
     *
     * @return list of {@link UserResponse} DTOs
     */
    @Transactional(readOnly = true)
    public List<UserResponse> getAllUsers() {
        return UserMapper.toResponseList(userRepository.findAll());
    }

    /**
     * Changes the role of a user.
     *
     * <p>An audit log entry is written for the {@code role} field with the old and new values.</p>
     *
     * @param id      the target user's primary key
     * @param newRole the new role to assign
     * @return the updated {@link UserResponse}
     * @throws ResourceNotFoundException if no user with the given ID exists
     */
    public UserResponse updateRole(Long id, UserRole newRole) {
        User user = findUserById(id);
        UserRole oldRole = user.getRole();

        user.setRole(newRole);
        User saved = userRepository.save(user);

        auditLogService.logChange("User", id, "role",
                oldRole != null ? oldRole.name() : null,
                newRole.name(),
                null);

        log.info("Role updated for user '{}': {} → {}", saved.getUsername(), oldRole, newRole);
        return UserMapper.toResponse(saved);
    }

    /**
     * Disables a user account (soft-delete).
     *
     * <p>The user record is never deleted physically — {@code enabled} is set to {@code false}.
     * A disabled user cannot authenticate. An admin may not disable their own account.</p>
     *
     * @param id the target user's primary key
     * @return the updated {@link UserResponse}
     * @throws ResourceNotFoundException if no user with the given ID exists
     * @throws IllegalStateException     if the requesting admin attempts to disable their own account
     */
    public UserResponse disableUser(Long id) {
        User user = findUserById(id);

        String currentUsername = resolveCurrentUsername();
        if (user.getUsername().equals(currentUsername)) {
            throw new IllegalStateException("An admin cannot disable their own account.");
        }

        user.setEnabled(false);
        User saved = userRepository.save(user);

        auditLogService.logChange("User", id, "enabled", "true", "false", null);

        log.info("User '{}' disabled by '{}'", saved.getUsername(), currentUsername);
        return UserMapper.toResponse(saved);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Loads a {@link User} by ID or throws {@link ResourceNotFoundException}.
     *
     * @param id the user's primary key
     * @return the user entity
     */
    private User findUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
    }

    /**
     * Resolves the username of the currently authenticated principal from
     * {@link SecurityContextHolder}.
     *
     * @return the authenticated username, or {@code "system"} as a fallback
     */
    private String resolveCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()
                || "anonymousUser".equals(auth.getPrincipal())) {
            return "system";
        }
        return auth.getName();
    }
}
