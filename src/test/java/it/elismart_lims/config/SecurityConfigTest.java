package it.elismart_lims.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import it.elismart_lims.security.JwtTokenProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for {@link SecurityConfig} CORS configuration and startup warnings.
 *
 * <p>Tests are pure unit tests (no Spring context) — the {@code corsAllowedOrigin}
 * field is set via {@link ReflectionTestUtils} to simulate what {@code @Value} injection
 * would do at runtime.</p>
 */
class SecurityConfigTest {

    private SecurityConfig securityConfig;
    private Logger securityConfigLogger;
    private ListAppender<ILoggingEvent> listAppender;

    @BeforeEach
    void setUp() {
        securityConfig = new SecurityConfig(
                mock(JwtTokenProvider.class),
                mock(UserDetailsService.class));

        securityConfigLogger = (Logger) LoggerFactory.getLogger(SecurityConfig.class);
        listAppender = new ListAppender<>();
        listAppender.start();
        securityConfigLogger.addAppender(listAppender);
    }

    @AfterEach
    void tearDown() {
        securityConfigLogger.detachAppender(listAppender);
    }

    // ── CORS origin used in CorsConfigurationSource ───────────────────────────

    /**
     * When {@code corsAllowedOrigin} is set to a custom origin, the CORS configuration
     * must allow exactly that origin — not the localhost default.
     */
    @Test
    void corsConfigurationSource_shouldUseConfiguredOrigin() {
        String customOrigin = "https://tunnel.example.pinggy.link";
        ReflectionTestUtils.setField(securityConfig, "corsAllowedOrigin", customOrigin);

        CorsConfigurationSource source = securityConfig.corsConfigurationSource();
        CorsConfiguration cors = source.getCorsConfiguration(new MockHttpServletRequest());

        assertThat(cors).isNotNull();
        assertThat(cors.getAllowedOrigins()).containsExactly(customOrigin);
    }

    /**
     * When {@code corsAllowedOrigin} is the localhost default, the CORS configuration
     * still uses that origin (the warning is informational; the app must start normally).
     */
    @Test
    void corsConfigurationSource_shouldUseLocalhostOrigin_whenDefault() {
        ReflectionTestUtils.setField(securityConfig, "corsAllowedOrigin",
                SecurityConfig.LOCALHOST_ORIGIN);

        CorsConfigurationSource source = securityConfig.corsConfigurationSource();
        CorsConfiguration cors = source.getCorsConfiguration(new MockHttpServletRequest());

        assertThat(cors).isNotNull();
        assertThat(cors.getAllowedOrigins()).containsExactly(SecurityConfig.LOCALHOST_ORIGIN);
    }

    // ── Startup warning when CORS origin is localhost ─────────────────────────

    /**
     * When the active origin is the localhost default, {@link SecurityConfig#corsConfigurationSource()}
     * must emit exactly one {@code WARN}-level message mentioning {@code localhost:8501}.
     * This warns deployers who forget to set {@code CORS_ALLOWED_ORIGIN} before tunnelling.
     */
    @Test
    void corsConfigurationSource_shouldEmitWarn_whenOriginIsLocalhost() {
        ReflectionTestUtils.setField(securityConfig, "corsAllowedOrigin",
                SecurityConfig.LOCALHOST_ORIGIN);

        securityConfig.corsConfigurationSource();

        assertThat(listAppender.list)
                .filteredOn(e -> e.getLevel() == Level.WARN)
                .anyMatch(e -> e.getFormattedMessage().contains("localhost:8501")
                        && e.getFormattedMessage().contains("CORS_ALLOWED_ORIGIN"));
    }

    /**
     * When the active origin is a non-localhost URL, no {@code WARN} should be logged.
     * The warning must not fire in a correctly configured deployment.
     */
    @Test
    void corsConfigurationSource_shouldNotEmitWarn_whenOriginIsNotLocalhost() {
        ReflectionTestUtils.setField(securityConfig, "corsAllowedOrigin",
                "https://tunnel.example.pinggy.link");

        securityConfig.corsConfigurationSource();

        assertThat(listAppender.list)
                .filteredOn(e -> e.getLevel() == Level.WARN)
                .isEmpty();
    }
}
