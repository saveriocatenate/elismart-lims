package it.elismart_lims.service;

import it.elismart_lims.dto.LoginRequest;
import it.elismart_lims.dto.LoginResponse;
import it.elismart_lims.dto.RegisterRequest;
import it.elismart_lims.dto.UserResponse;
import it.elismart_lims.mapper.UserMapper;
import it.elismart_lims.model.User;
import it.elismart_lims.repository.UserRepository;
import it.elismart_lims.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Handles user authentication and JWT token issuance.
 *
 * <p>Delegates credential verification to Spring Security's
 * {@link AuthenticationManager}. On success, loads the full {@link User} entity
 * to include the {@link it.elismart_lims.model.UserRole} in the generated token
 * and response DTO.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Authenticates a user and returns a signed JWT on success.
     *
     * <p>Throws {@link org.springframework.security.core.AuthenticationException}
     * (e.g. {@code BadCredentialsException}) if credentials are invalid; the
     * controller maps this to HTTP 401.</p>
     *
     * @param request the login credentials
     * @return a {@link LoginResponse} containing the JWT, username, and role
     * @throws UsernameNotFoundException if the user is not found after authentication
     *                                   (should never occur in normal flow)
     */
    public LoginResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password())
        );

        String username = authentication.getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        String token = jwtTokenProvider.generateToken(username, user.getRole());
        log.info("User '{}' authenticated successfully with role '{}'", username, user.getRole());

        return LoginResponse.builder()
                .token(token)
                .username(username)
                .role(user.getRole().name())
                .build();
    }

    /**
     * Registers a new user in the system.
     *
     * <p>The plaintext password is BCrypt-hashed before persistence.
     * Throws {@link IllegalStateException} (→ HTTP 409) if the username is already taken.</p>
     *
     * @param request the registration payload containing username, password, and role
     * @return a {@link UserResponse} for the newly created user
     * @throws IllegalStateException if the requested username already exists
     */
    public UserResponse register(RegisterRequest request) {
        if (userRepository.findByUsername(request.username()).isPresent()) {
            throw new IllegalStateException("Username already exists: " + request.username());
        }
        User user = User.builder()
                .username(request.username())
                .password(passwordEncoder.encode(request.password()))
                .role(request.role())
                .build();
        User saved = userRepository.save(user);
        log.info("Registered new user '{}' with role '{}'", saved.getUsername(), saved.getRole());
        return UserMapper.toResponse(saved);
    }
}
