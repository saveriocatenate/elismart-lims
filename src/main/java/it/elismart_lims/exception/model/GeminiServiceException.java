package it.elismart_lims.exception.model;

/**
 * Thrown when the Google Gemini API call fails or returns a response
 * that cannot be parsed into the expected structure.
 *
 * <p>This exception wraps upstream errors from an external AI service and maps to
 * HTTP 502 Bad Gateway in the {@link it.elismart_lims.exception.GlobalExceptionHandler}.</p>
 */
public class GeminiServiceException extends RuntimeException {

    /**
     * Constructs a new GeminiServiceException with the given message.
     *
     * @param message description of the failure
     */
    public GeminiServiceException(String message) {
        super(message);
    }

    /**
     * Constructs a new GeminiServiceException with the given message and cause.
     *
     * @param message description of the failure
     * @param cause   the underlying exception from the Gemini API or JSON parser
     */
    public GeminiServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
