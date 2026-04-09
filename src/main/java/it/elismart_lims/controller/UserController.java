package it.elismart_lims.controller;

import it.elismart_lims.dto.RoleUpdateRequest;
import it.elismart_lims.dto.UserResponse;
import it.elismart_lims.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for user management operations.
 *
 * <p>All endpoints in this controller are restricted to users with the {@code ADMIN} role.
 * The restriction is enforced by {@code @PreAuthorize("hasRole('ADMIN')")} on the class,
 * which is evaluated by Spring's method security mechanism (enabled via
 * {@link org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity}).</p>
 *
 * <p>Available endpoints:
 * <ul>
 *   <li>{@code GET  /api/users}           — list all users</li>
 *   <li>{@code PUT  /api/users/{id}/role} — change a user's role</li>
 *   <li>{@code DELETE /api/users/{id}}    — soft-disable a user (sets enabled=false)</li>
 * </ul>
 * </p>
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class UserController {

    private final UserService userService;

    /**
     * Returns the full list of registered users.
     *
     * @return 200 OK with a list of {@link UserResponse} DTOs
     */
    @GetMapping
    public ResponseEntity<List<UserResponse>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    /**
     * Changes the role of the specified user.
     *
     * @param id      the target user's primary key
     * @param request the new role; must not be {@code null}
     * @return 200 OK with the updated {@link UserResponse}
     */
    @PutMapping("/{id}/role")
    public ResponseEntity<UserResponse> updateRole(
            @PathVariable Long id,
            @Valid @RequestBody RoleUpdateRequest request) {
        return ResponseEntity.ok(userService.updateRole(id, request.role()));
    }

    /**
     * Disables the specified user account (soft-delete).
     *
     * <p>The account is not physically removed — {@code enabled} is set to {@code false}.
     * An admin cannot disable their own account.</p>
     *
     * @param id the target user's primary key
     * @return 200 OK with the updated {@link UserResponse}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<UserResponse> disableUser(@PathVariable Long id) {
        return ResponseEntity.ok(userService.disableUser(id));
    }
}
