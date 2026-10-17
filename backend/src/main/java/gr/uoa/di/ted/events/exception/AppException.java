package gr.uoa.di.ted.events.exception;

import org.springframework.http.HttpStatus;

/**
 * Base class for all domain-level exceptions in this application.
 *
 * Subclasses supply the HTTP status and a machine-readable error code so the
 * GlobalExceptionHandler can produce a consistent JSON error response without
 * needing a separate handler for every exception type.
 */
public class AppException extends RuntimeException {

    private final HttpStatus status;
    private final String     code;

    public AppException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code   = code;
    }

    public HttpStatus getStatus() { return status; }
    public String     getCode()   { return code; }
}
