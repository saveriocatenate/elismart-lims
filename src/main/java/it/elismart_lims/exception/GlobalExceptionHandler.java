package it.elismart_lims.exception;

import it.elismart_lims.exception.model.GeminiServiceException;
import it.elismart_lims.exception.model.ProtocolMismatchException;
import it.elismart_lims.exception.model.ResourceNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Global exception handler for REST API error responses.
 *
 * <p>Maps domain exceptions to appropriate HTTP status codes and a uniform JSON error body
 * containing {@code status}, {@code error}, {@code message}, and {@code timestamp}.</p>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handles {@link ResourceNotFoundException} — returns HTTP 404 Not Found.
     *
     * @param ex the exception
     * @return a 404 error response body
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(ResourceNotFoundException ex) {
        log.warn("Resource not found: {}", ex.getMessage());
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    /**
     * Handles {@link ProtocolMismatchException} — returns HTTP 400 Bad Request.
     *
     * @param ex the exception
     * @return a 400 error response body
     */
    @ExceptionHandler(ProtocolMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleProtocolMismatch(ProtocolMismatchException ex) {
        log.warn("Protocol mismatch: {}", ex.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    /**
     * Handles {@link MethodArgumentNotValidException} — returns HTTP 400 Bad Request
     * with all field validation errors concatenated.
     *
     * @param ex the exception
     * @return a 400 error response body listing each field error
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        String errors = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("Validation failed: {}", errors);
        return buildResponse(HttpStatus.BAD_REQUEST, errors);
    }

    /**
     * Handles {@link IllegalArgumentException} — returns HTTP 400 Bad Request.
     *
     * @param ex the exception
     * @return a 400 error response body
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Illegal argument: {}", ex.getMessage());
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    /**
     * Handles {@link IllegalStateException} — returns HTTP 409 Conflict.
     * Used when an operation is blocked by dependent data (e.g. protocol has linked experiments).
     *
     * @param ex the exception
     * @return a 409 error response body
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalState(IllegalStateException ex) {
        log.warn("Illegal state: {}", ex.getMessage());
        return buildResponse(HttpStatus.CONFLICT, ex.getMessage());
    }

    /**
     * Handles {@link GeminiServiceException} — returns HTTP 502 Bad Gateway,
     * indicating the upstream Gemini AI service failed or returned an unexpected response.
     *
     * @param ex the exception
     * @return a 502 error response body
     */
    @ExceptionHandler(GeminiServiceException.class)
    public ResponseEntity<Map<String, Object>> handleGeminiService(GeminiServiceException ex) {
        log.error("Gemini service error: {}", ex.getMessage(), ex);
        return buildResponse(HttpStatus.BAD_GATEWAY, "AI service error: " + ex.getMessage());
    }

    /**
     * Builds a uniform error response body.
     *
     * @param status  the HTTP status to return
     * @param message the error message to include
     * @return the {@link ResponseEntity} with error body
     */
    private ResponseEntity<Map<String, Object>> buildResponse(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of(
                "status", status.value(),
                "error", status.getReasonPhrase(),
                "message", message,
                "timestamp", LocalDateTime.now().toString()
        ));
    }
}
