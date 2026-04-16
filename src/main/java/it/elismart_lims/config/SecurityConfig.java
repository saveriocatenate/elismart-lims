package it.elismart_lims.config;

import it.elismart_lims.security.JwtAuthFilter;
import it.elismart_lims.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Spring Security configuration for stateless JWT-based authentication.
 *
 * <p>Endpoint access rules:
 * <ul>
 *   <li>{@code POST /api/auth/login} — public (no token required)</li>
 *   <li>{@code GET /api/health} — public</li>
 *   <li>{@code GET /api-docs/**}, {@code GET /swagger-ui/**} — public (OpenAPI)</li>
 *   <li>{@code DELETE /api/**} — requires {@code ADMIN} role</li>
 *   <li>{@code POST /api/auth/register} — requires {@code ADMIN} role</li>
 *   <li>{@code POST /api/protocols} — requires {@code ADMIN} role</li>
 *   <li>{@code PUT /api/protocols/**} — requires {@code ADMIN} role</li>
 *   <li>{@code PUT /api/experiments/*&#47;status} — requires {@code REVIEWER} or {@code ADMIN} role</li>
 *   <li>All other endpoints — require any authenticated user</li>
 * </ul>
 *
 * <p>CORS allows requests from the Streamlit frontend at the origin configured via
 * {@code CORS_ALLOWED_ORIGIN} (defaults to {@code http://localhost:8501}).</p>
 */
@Slf4j
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    /** Localhost origin used as the default fallback when {@code CORS_ALLOWED_ORIGIN} is not set. */
    static final String LOCALHOST_ORIGIN = "http://localhost:8501";

    public static final String ADMIN = "ADMIN";
    private final JwtTokenProvider jwtTokenProvider;
    private final UserDetailsService userDetailsService;

    /**
     * The allowed CORS origin for the frontend, read from {@code cors.allowed-origin}
     * (environment variable {@code CORS_ALLOWED_ORIGIN}). Defaults to {@code http://localhost:8501}.
     *
     * <p>A startup warning is emitted when the default localhost value is active so that
     * deployers remember to set {@code CORS_ALLOWED_ORIGIN} before exposing the backend
     * via a tunnel or remote URL.</p>
     */
    @Value("${cors.allowed-origin}")
    private String corsAllowedOrigin;

    /**
     * Configures the main security filter chain.
     *
     * @param http the {@link HttpSecurity} builder
     * @return the configured {@link SecurityFilterChain}
     * @throws Exception if configuration fails
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/health").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        // ADMIN-only: all delete operations, protocol mutations, user registration
                        .requestMatchers(HttpMethod.DELETE, "/api/**").hasRole(ADMIN)
                        .requestMatchers(HttpMethod.POST, "/api/auth/register").hasRole(ADMIN)
                        .requestMatchers(HttpMethod.POST, "/api/protocols").hasRole(ADMIN)
                        .requestMatchers(HttpMethod.PUT, "/api/protocols/**").hasRole(ADMIN)
                        // REVIEWER or ADMIN: experiment status transitions
                        .requestMatchers(HttpMethod.PUT, "/api/experiments/*/status").hasAnyRole("REVIEWER", ADMIN)
                        // All other requests require any authenticated user
                        .anyRequest().authenticated()
                )
                .addFilterBefore(new JwtAuthFilter(jwtTokenProvider),
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * CORS configuration allowing the Streamlit frontend origin read from
     * {@code cors.allowed-origin} (env var {@code CORS_ALLOWED_ORIGIN}).
     *
     * <p>A {@code WARN}-level message is emitted at bean creation time when the active origin
     * is {@value #LOCALHOST_ORIGIN}, reminding operators to set {@code CORS_ALLOWED_ORIGIN}
     * before exposing the backend via a tunnel or remote URL. The application starts normally
     * regardless — localhost is a valid origin for local development.</p>
     *
     * @return the CORS configuration source
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        if (LOCALHOST_ORIGIN.equals(corsAllowedOrigin)) {
            log.warn("CORS origin is set to localhost:8501. If the frontend is accessed via tunnel "
                    + "or remote URL, API calls will be blocked. Set CORS_ALLOWED_ORIGIN in .env");
        }

        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(corsAllowedOrigin));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    /**
     * BCrypt password encoder bean used for hashing and verifying passwords.
     *
     * @return a {@link BCryptPasswordEncoder} instance
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Exposes the {@link AuthenticationManager} as a bean for use in the auth controller.
     *
     * @param authenticationConfiguration Spring's authentication configuration
     * @return the configured {@link AuthenticationManager}
     * @throws Exception if the manager cannot be retrieved
     */
    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    /**
     * Wires the DAO authentication provider with the custom user details service and
     * password encoder, so Spring's default form-login flow uses them too.
     *
     * @return a configured {@link DaoAuthenticationProvider}
     */
    @Bean
    public DaoAuthenticationProvider daoAuthenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }
}
