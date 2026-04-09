package it.elismart_lims.security;

import it.elismart_lims.model.UserRole;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Servlet filter that extracts and validates a JWT Bearer token on every request.
 *
 * <p>If a valid token is found in the {@code Authorization: Bearer <token>} header,
 * the filter sets an authenticated {@link UsernamePasswordAuthenticationToken} in the
 * {@link SecurityContextHolder}. Requests without a valid token reach the
 * {@link SecurityConfig} access rules unauthenticated.</p>
 *
 * <p>This filter is intentionally NOT a Spring {@code @Component} — it is registered
 * explicitly in {@link SecurityConfig} to avoid double-registration.</p>
 */
@Slf4j
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String token = extractToken(request);

        if (token != null && jwtTokenProvider.validateToken(token)) {
            String username = jwtTokenProvider.getUsernameFromToken(token);
            UserRole role = jwtTokenProvider.getRoleFromToken(token);

            var authority = new SimpleGrantedAuthority("ROLE_" + role.name());
            var authentication = new UsernamePasswordAuthenticationToken(
                    username, null, List.of(authority));
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

            SecurityContextHolder.getContext().setAuthentication(authentication);
            log.debug("Authenticated user '{}' with role '{}'", username, role);
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Extracts the raw token from the {@code Authorization} header.
     *
     * @param request the incoming HTTP request
     * @return the token string, or {@code null} if the header is absent or malformed
     */
    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}
