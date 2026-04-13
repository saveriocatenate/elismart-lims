package it.elismart_lims.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Servlet filter that logs every HTTP request and response at DEBUG level.
 *
 * <p>Each request is logged with method, URI, query string, and body.
 * Each response is logged with the HTTP status code and body.
 * Both are wrapped with caching wrappers so the body can be read without
 * consuming the stream used by downstream handlers.</p>
 *
 * <p>Logging is suppressed unless the DEBUG level is active for this class,
 * so this filter has no effective overhead in non-debug environments.</p>
 */
@Slf4j
@Component
public class HttpLoggingFilter extends OncePerRequestFilter {

    /** Maximum number of body bytes to log. Prevents flooding logs with large payloads. */
    private static final int MAX_BODY_BYTES = 10_000;

    /**
     * Wraps the request and response in caching wrappers, delegates to the filter chain,
     * then logs the captured request and response details.
     *
     * @param request     the incoming HTTP request
     * @param response    the outgoing HTTP response
     * @param filterChain the remaining filter chain
     * @throws ServletException if a servlet error occurs
     * @throws IOException      if an I/O error occurs
     */
    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        if (!log.isDebugEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        ContentCachingRequestWrapper requestWrapper = new ContentCachingRequestWrapper(request, MAX_BODY_BYTES);
        ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);

        try {
            filterChain.doFilter(requestWrapper, responseWrapper);
        } finally {
            logRequest(requestWrapper);
            logResponse(responseWrapper);
            responseWrapper.copyBodyToResponse();
        }
    }

    /**
     * Logs the request method, URI, optional query string, and body.
     *
     * @param request the caching request wrapper after the filter chain has run
     */
    private void logRequest(ContentCachingRequestWrapper request) {
        String query = request.getQueryString() != null ? "?" + request.getQueryString() : "";
        String body = extractBody(request.getContentAsByteArray());
        log.debug("--> {} {}{}{}", request.getMethod(), request.getRequestURI(), query,
                body.isEmpty() ? "" : "\n" + body);
    }

    /**
     * Logs the response status and body.
     *
     * @param response the caching response wrapper after the filter chain has run
     */
    private void logResponse(ContentCachingResponseWrapper response) {
        String body = extractBody(response.getContentAsByteArray());
        log.debug("<-- {}{}", response.getStatus(),
                body.isEmpty() ? "" : "\n" + body);
    }

    /**
     * Converts a byte array to a UTF-8 string, truncating if it exceeds {@link #MAX_BODY_BYTES}.
     *
     * @param bytes the raw byte content
     * @return the decoded string, or an empty string if the content is blank
     */
    private String extractBody(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }
        String body = new String(bytes, StandardCharsets.UTF_8).strip();
        if (body.isEmpty()) {
            return "";
        }
        if (bytes.length >= MAX_BODY_BYTES) {
            return body + " [TRUNCATED]";
        }
        return body;
    }
}
