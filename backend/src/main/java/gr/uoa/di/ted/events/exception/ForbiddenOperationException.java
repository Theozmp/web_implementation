package gr.uoa.di.ted.events.exception;

import org.springframework.http.HttpStatus;

/** Thrown when an authenticated user attempts an action they don't own (403). */
public class ForbiddenOperationException extends AppException {
    public ForbiddenOperationException(String message) {
        super(HttpStatus.FORBIDDEN, "FORBIDDEN", message);
    }
}
