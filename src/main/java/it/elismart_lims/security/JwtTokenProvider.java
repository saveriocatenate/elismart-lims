package it.elismart_lims.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import it.elismart_lims.model.UserRole;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * Generates and validates JWT tokens for stateless authentication.
 *
 * <p>Tokens are signed with HMAC-SHA256 using the secret configured in
 * {@code jwt.secret}. The {@code role} claim stores the user's {@link UserRole}
 * string value, which {@link JwtAuthFilter} reads to populate the security context.</p>
 */
@Slf4j
@Component
public class JwtTokenProvider {

    private final SecretKey secretKey;
    private final long expirationMs;

    /**
     * Constructs the provider, deriving the signing key from the configured secret.
     *
     * <p>The secret is validated at startup: it must be non-blank, at least 32 characters
     * long, and must not be a known placeholder value. The application will refuse to start
     * with an invalid secret so that a misconfigured deployment is caught immediately rather
     * than silently accepting forgeable tokens.</p>
     *
     * @param secret       raw secret string read from {@code JWT_SECRET} env var (min 32 chars)
     * @param expirationMs token lifetime in milliseconds
     * @throws IllegalStateException if the secret is missing, too short, or a placeholder
     */
    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration-ms}") long expirationMs) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "JWT_SECRET environment variable must be set. "
                    + "Generate a strong value with: openssl rand -base64 32");
        }
        if (secret.length() < 32) {
            throw new IllegalStateException(
                    "JWT_SECRET must be at least 32 characters long. "
                    + "Current length: " + secret.length() + ". "
                    + "Generate a strong value with: openssl rand -base64 32");
        }
        if (secret.contains("dev-secret") || secret.contains("change-in-production")) {
            throw new IllegalStateException(
                    "JWT_SECRET appears to be a placeholder value and cannot be used. "
                    + "Generate a strong value with: openssl rand -base64 32");
        }
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    /**
     * Generates a signed JWT for the given user.
     *
     * @param username the authenticated user's login name (becomes the subject)
     * @param role     the user's role (stored as a claim named {@code role})
     * @return compact, URL-safe JWT string
     */
    public String generateToken(String username, UserRole role) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .subject(username)
                .claim("role", role.name())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(secretKey)
                .compact();
    }

    /**
     * Extracts the username (subject) from a valid JWT.
     *
     * @param token the compact JWT string
     * @return the subject claim value
     */
    public String getUsernameFromToken(String token) {
        return parseClaims(token).getSubject();
    }

    /**
     * Extracts the role from a valid JWT.
     *
     * @param token the compact JWT string
     * @return the {@link UserRole} encoded in the {@code role} claim
     */
    public UserRole getRoleFromToken(String token) {
        String roleName = parseClaims(token).get("role", String.class);
        return UserRole.valueOf(roleName);
    }

    /**
     * Validates the token's signature and expiry.
     *
     * @param token the compact JWT string
     * @return {@code true} if the token is well-formed and unexpired
     */
    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Parses and verifies the JWT, returning its claims payload.
     *
     * @param token the compact JWT string
     * @return the verified {@link Claims}
     * @throws JwtException if the token is invalid or expired
     */
    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
