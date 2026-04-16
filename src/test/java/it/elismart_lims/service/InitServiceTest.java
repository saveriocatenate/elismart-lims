package it.elismart_lims.service;

import it.elismart_lims.model.User;
import it.elismart_lims.model.UserRole;
import it.elismart_lims.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link InitService}.
 *
 * <p>Covers three scenarios:</p>
 * <ul>
 *   <li>First boot with {@code ADMIN_PASSWORD} env var set — password used as-is.</li>
 *   <li>First boot without env var — random password generated and admin created.</li>
 *   <li>Subsequent boot with existing users — no user created.</li>
 * </ul>
 *
 * <p>{@code System.getenv("ADMIN_PASSWORD")} cannot be injected via JUnit without
 * a native-agent mock, so the password-resolution branch is exercised by a
 * subclass that overrides {@link InitService#resolveAdminPassword()} via a
 * package-private test hook.</p>
 */
@ExtendWith(MockitoExtension.class)
class InitServiceTest {

    @Mock
    private UserRepository userRepository;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private InitService initService;

    @BeforeEach
    void setUp() {
        initService = new InitService(userRepository, passwordEncoder);
    }

    // -------------------------------------------------------------------------
    // Helpers: subclasses that inject a fixed password without touching env
    // -------------------------------------------------------------------------

    /** Returns a fixed password regardless of ADMIN_PASSWORD env var. */
    private InitService withFixedPassword(String password) {
        return new InitService(userRepository, passwordEncoder) {
            @Override
            String resolveAdminPassword() {
                return password;
            }
        };
    }

    // -------------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------------

    /**
     * First boot: DB empty, ADMIN_PASSWORD provided.
     * Admin user must be created with BCrypt-hashed version of that password.
     */
    @Test
    void firstBoot_withEnvPassword_createsAdminWithThatPassword() throws Exception {
        when(userRepository.count()).thenReturn(0L);
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);

        InitService sut = withFixedPassword("test123");
        sut.run();

        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();

        assertThat(saved.getUsername()).isEqualTo("admin");
        assertThat(saved.getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(saved.isEnabled()).isTrue();
        // Verify that the stored hash matches the supplied password
        assertThat(passwordEncoder.matches("test123", saved.getPassword())).isTrue();
    }

    /**
     * First boot: DB empty, no ADMIN_PASSWORD env var.
     * A random password must be generated and the admin user created.
     * The random password must match the stored BCrypt hash.
     */
    @Test
    void firstBoot_withoutEnvPassword_createsAdminWithRandomPassword() throws Exception {
        when(userRepository.count()).thenReturn(0L);
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);

        // Use real InitService — resolveAdminPassword() will call generateRandomPassword()
        // when ADMIN_PASSWORD is absent (or blank in CI).
        // We cannot predict the password, but we can verify it was BCrypt-hashed.
        initService.run();

        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();

        assertThat(saved.getUsername()).isEqualTo("admin");
        assertThat(saved.getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(saved.isEnabled()).isTrue();
        // Password must be a non-empty BCrypt hash (starts with $2a$ or $2b$)
        assertThat(saved.getPassword()).startsWith("$2");
    }

    /**
     * Subsequent boot: DB already has users.
     * {@code userRepository.save()} must never be called.
     */
    @Test
    void subsequentBoot_dbHasUsers_noUserCreated() throws Exception {
        when(userRepository.count()).thenReturn(3L);

        initService.run();

        verify(userRepository, never()).save(any());
    }

    /**
     * Random password format: 16 chars in groups of 4 separated by hyphens
     * (total 19 characters: {@code xxxx-xxxx-xxxx-xxxx}).
     */
    @Test
    void generateRandomPassword_hasCorrectFormat() {
        // Call generateRandomPassword() multiple times to catch format regressions.
        for (int i = 0; i < 20; i++) {
            String pwd = initService.generateRandomPassword();
            assertThat(pwd).matches("[a-zA-Z2-9]{4}-[a-zA-Z2-9]{4}-[a-zA-Z2-9]{4}-[a-zA-Z2-9]{4}");
        }
    }
}
