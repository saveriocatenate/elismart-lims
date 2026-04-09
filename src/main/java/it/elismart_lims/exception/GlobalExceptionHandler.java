package it.elismart_lims.exception;

import it.elismart_lims.exception.model.GeminiServiceException;
import it.elismart_lims.exception.model.ProtocolMismatchException;
import it.elismart_lims.exception.model.ResourceNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Global exception handler for REST API error responses.
 *
 * <p>Maps domain exceptions and Spring framework exceptions to appropriate HTTP status
 * codes and a uniform JSON error body containing {@code status}, {@code error},
 * {@code message}, and {@code timestamp}.</p>
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
     * Handles {@link HttpMessageNotReadableException} — returns HTTP 400 Bad Request.
     *
     * <p>Triggered by malformed JSON, wrong field types (e.g. string where a number is
     * expected), or unknown enum values. Returns a sanitised message without internal
     * class names to avoid leaking implementation details.</p>
     *
     * @param ex the exception
     * @return a 400 error response body
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleMessageNotReadable(HttpMessageNotReadableException ex) {
        String cause = ex.getMostSpecificCause().getMessage();
        log.warn("Malformed request body: {}", cause);
        return buildResponse(HttpStatus.BAD_REQUEST, "Invalid request body: " + cause);
    }

    /**
     * Handles {@link MethodArgumentTypeMismatchException} — returns HTTP 400 Bad Request.
     *
     * <p>Triggered when a path or query parameter cannot be converted to the expected type
     * (e.g. {@code GET /api/protocols/abc} where {@code abc} cannot be parsed as a Long).</p>
     *
     * @param ex the exception
     * @return a 400 error response body with the parameter name and expected type
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String expected = ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "unknown";
        String message = String.format("Invalid value '%s' for parameter '%s': expected %s",
                ex.getValue(), ex.getName(), expected);
        log.warn("Type mismatch: {}", message);
        return buildResponse(HttpStatus.BAD_REQUEST, message);
    }

    /**
     * Handles {@link DataIntegrityViolationException} — returns HTTP 409 Conflict.
     *
     * <p>Triggered by unique constraint violations or foreign key violations at the
     * database level. The handler inspects the root cause message and returns a
     * user-friendly description without leaking SQL or table names.</p>
     *
     * @param ex the exception
     * @return a 409 error response body
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataIntegrity(DataIntegrityViolationException ex) {
        String rootMsg = ex.getMostSpecificCause().getMessage();
        log.warn("Data integrity violation: {}", rootMsg);
        String message;
        if (rootMsg != null && rootMsg.toUpperCase().contains("UNIQUE")) {
            message = "A record with the same unique identifier already exists.";
        } else if (rootMsg != null && rootMsg.toUpperCase().contains("FOREIGN KEY")) {
            message = "Cannot complete operation: this record is referenced by other data.";
        } else {
            message = "Database constraint violation.";
        }
        return buildResponse(HttpStatus.CONFLICT, message);
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
    /**
     * Handles {@link GeminiServiceException} — returns the HTTP status carried by the exception
     * (401, 429, 502, or 504), indicating which upstream failure occurred.
     *
     * @param ex the exception
     * @return an error response body with the appropriate status
     */
    @ExceptionHandler(GeminiServiceException.class)
    public ResponseEntity<Map<String, Object>> handleGeminiService(GeminiServiceException ex) {
        log.error("Gemini service error: {}", ex.getMessage(), ex);
        HttpStatus status = HttpStatus.resolve(ex.getHttpStatus());
        if (status == null) {
            status = HttpStatus.BAD_GATEWAY;
        }
        return buildResponse(status, "AI service error: " + ex.getMessage());
    }

    /**
     * Catch-all handler for any unhandled exception — returns HTTP 500 Internal Server Error.
     *
     * <p>This prevents Spring's default error page from leaking stack traces or internal
     * class names to the client. The exception is logged in full for diagnosis.</p>
     *
     * @param ex the exception
     * @return a 500 error response body
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
        log.error("Unhandled exception [{}]: {}", ex.getClass().getSimpleName(), ex.getMessage(), ex);
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred.");
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
