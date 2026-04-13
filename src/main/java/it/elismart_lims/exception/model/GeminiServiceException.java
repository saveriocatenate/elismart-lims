package it.elismart_lims.exception.model;

import lombok.Getter;

/**
 * Thrown when the Google Gemini API call fails or returns a response
 * that cannot be parsed into the expected structure.
 *
 * <p>The {@link #httpStatus} field carries the HTTP status code that the
 * {@link it.elismart_lims.exception.GlobalExceptionHandler} should forward to the client.
 * Different failure modes map to different statuses:</p>
 * <ul>
 *   <li>401 — invalid or missing API key</li>
 *   <li>429 — rate limit exceeded</li>
 *   <li>504 — upstream request timed out</li>
 *   <li>502 — any other Gemini API failure (default)</li>
 * </ul>
 */
@Getter
public class GeminiServiceException extends RuntimeException {

    /** HTTP status code to return to the caller. Defaults to 502 Bad Gateway. */
    private final int httpStatus;

    /**
     * Constructs a new GeminiServiceException with the given message and a default 502 status.
     *
     * @param message description of the failure
     */
    public GeminiServiceException(String message) {
        super(message);
        this.httpStatus = 502;
    }

    /**
     * Constructs a new GeminiServiceException with the given message, cause, and a default 502 status.
     *
     * @param message description of the failure
     * @param cause   the underlying exception from the Gemini API
     */
    public GeminiServiceException(String message, Throwable cause) {
        super(message, cause);
        this.httpStatus = 502;
    }

    /**
     * Constructs a new GeminiServiceException with an explicit HTTP status code.
     *
     * @param message    description of the failure
     * @param cause      the underlying exception from the Gemini API
     * @param httpStatus the HTTP status code to forward to the caller (e.g. 401, 429, 502, 504)
     */
    public GeminiServiceException(String message, Throwable cause, int httpStatus) {
        super(message, cause);
        this.httpStatus = httpStatus;
    }

}
