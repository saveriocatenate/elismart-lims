package it.elismart_lims.service;

import it.elismart_lims.dto.LoginRequest;
import it.elismart_lims.dto.LoginResponse;
import it.elismart_lims.dto.RegisterRequest;
import it.elismart_lims.dto.UserResponse;
import it.elismart_lims.model.User;
import it.elismart_lims.model.UserRole;
import it.elismart_lims.repository.UserRepository;
import it.elismart_lims.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AuthService}.
 *
 * <p>Covers successful login/register flows and rejection paths
 * (bad credentials, duplicate username).</p>
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AuthService authService;

    private User adminUser;

    @BeforeEach
    void setUp() {
        adminUser = User.builder()
                .id(1L)
                .username("admin")
                .password("encoded-password")
                .role(UserRole.ADMIN)
                .enabled(true)
                .build();
    }

    // ─────────────────────────────── login ────────────────────────────────────

    @Test
    void login_shouldReturnToken_whenCredentialsAreValid() {
        Authentication auth = new UsernamePasswordAuthenticationToken("admin", "admin");
        when(authenticationManager.authenticate(any())).thenReturn(auth);
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(adminUser));
        when(jwtTokenProvider.generateToken("admin", UserRole.ADMIN)).thenReturn("jwt-token");

        LoginResponse response = authService.login(new LoginRequest("admin", "admin"));

        assertThat(response.token()).isEqualTo("jwt-token");
        assertThat(response.username()).isEqualTo("admin");
        assertThat(response.role()).isEqualTo("ADMIN");
    }

    @Test
    void login_shouldThrow_whenCredentialsAreInvalid() {
        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        var login = new LoginRequest("admin", "wrong");
        assertThatThrownBy(() -> authService.login(login))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void login_shouldThrow_whenUserNotFoundAfterAuthentication() {
        Authentication auth = new UsernamePasswordAuthenticationToken("ghost", "password");
        when(authenticationManager.authenticate(any())).thenReturn(auth);
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("ghost", "password")))
                .isInstanceOf(org.springframework.security.core.userdetails.UsernameNotFoundException.class)
                .hasMessageContaining("ghost");
    }

    // ─────────────────────────────── register ─────────────────────────────────

    @Test
    void register_shouldCreateUser_whenUsernameIsNew() {
        RegisterRequest request = new RegisterRequest("newuser", "secret", UserRole.ANALYST);
        when(userRepository.findByUsername("newuser")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("secret")).thenReturn("encoded-secret");

        User saved = User.builder()
                .id(2L)
                .username("newuser")
                .password("encoded-secret")
                .role(UserRole.ANALYST)
                .enabled(true)
                .build();
        when(userRepository.save(any(User.class))).thenReturn(saved);

        UserResponse response = authService.register(request);

        assertThat(response.username()).isEqualTo("newuser");
        assertThat(response.role()).isEqualTo(UserRole.ANALYST);
        verify(passwordEncoder).encode("secret");
        verify(userRepository).save(any(User.class));
    }

    @Test
    void register_shouldThrow_whenUsernameAlreadyExists() {
        RegisterRequest request = new RegisterRequest("admin", "password", UserRole.ANALYST);
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(adminUser));

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("admin");
    }

    @Test
    void register_shouldEncodePasswordBeforeSaving() {
        RegisterRequest request = new RegisterRequest("analyst1", "plaintext", UserRole.ANALYST);
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.empty());
        when(passwordEncoder.encode("plaintext")).thenReturn("bcrypt-hash");

        User saved = User.builder()
                .id(3L)
                .username("analyst1")
                .password("bcrypt-hash")
                .role(UserRole.ANALYST)
                .enabled(true)
                .build();
        when(userRepository.save(any(User.class))).thenReturn(saved);

        authService.register(request);

        verify(passwordEncoder).encode("plaintext");
    }
}
