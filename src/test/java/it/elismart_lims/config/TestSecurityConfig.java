package it.elismart_lims.config;

import it.elismart_lims.security.JwtTokenProvider;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.SecurityFilterChain;

import static org.mockito.Mockito.mock;

/**
 * Test-only security configuration that disables authentication for all
 * {@code @WebMvcTest} slices.
 *
 * <p>Import this class with {@code @Import(TestSecurityConfig.class)} on every
 * {@code @WebMvcTest} test class. It provides:
 * <ul>
 *   <li>A permissive {@link SecurityFilterChain} (all requests permitted, CSRF off).</li>
 *   <li>Mock {@link JwtTokenProvider} and {@link UserDetailsService} beans so that
 *       {@link it.elismart_lims.config.SecurityConfig} can be loaded without wiring
 *       real dependencies.</li>
 * </ul>
 */
@TestConfiguration
public class TestSecurityConfig {

    /**
     * Permissive filter chain that allows all requests without authentication.
     * {@code @Order(1)} ensures it takes precedence over the production chain.
     *
     * @param http the {@link HttpSecurity} builder
     * @return the permissive {@link SecurityFilterChain}
     * @throws Exception if configuration fails
     */
    @Bean
    @Order(1)
    public SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }

    /**
     * Mock {@link JwtTokenProvider} bean — satisfies the dependency in
     * {@link it.elismart_lims.config.SecurityConfig} without requiring a real JWT secret.
     *
     * @return a Mockito mock
     */
    @Bean
    @Primary
    public JwtTokenProvider jwtTokenProvider() {
        return mock(JwtTokenProvider.class);
    }

    /**
     * Mock {@link UserDetailsService} bean — satisfies the dependency in
     * {@link it.elismart_lims.config.SecurityConfig} without a real database.
     *
     * @return a Mockito mock
     */
    @Bean
    @Primary
    public UserDetailsService userDetailsService() {
        return mock(UserDetailsService.class);
    }
}
