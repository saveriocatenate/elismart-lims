package it.elismart_lims.controller;

import it.elismart_lims.dto.LoginRequest;
import it.elismart_lims.dto.LoginResponse;
import it.elismart_lims.dto.RegisterRequest;
import it.elismart_lims.dto.UserResponse;
import it.elismart_lims.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for authentication endpoints.
 *
 * <p>The {@code /api/auth/login} endpoint is explicitly permitted without a JWT
 * in {@link it.elismart_lims.config.SecurityConfig}.</p>
 *
 * <p>The {@code /api/auth/register} endpoint requires the {@code ADMIN} role.</p>
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * Authenticates a user and returns a signed JWT token.
     *
     * @param request the login credentials; both fields are mandatory
     * @return 200 OK with {@link LoginResponse} on success, or 401 Unauthorized
     *         if credentials are invalid
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        try {
            LoginResponse response = authService.login(request);
            return ResponseEntity.ok(response);
        } catch (AuthenticationException ex) {
            return ResponseEntity.status(401)
                    .body(java.util.Map.of(
                            "status", 401,
                            "error", "Unauthorized",
                            "message", "Invalid username or password.",
                            "timestamp", java.time.LocalDateTime.now().toString()
                    ));
        }
    }

    /**
     * Creates a new user account. Restricted to {@code ADMIN} role.
     *
     * <p>The plaintext password is BCrypt-hashed before persistence. Returns HTTP 409
     * if the requested username is already taken.</p>
     *
     * @param request the registration payload; all fields are mandatory
     * @return 201 Created with the new {@link UserResponse}, 409 if username is duplicate
     */
    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        UserResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
