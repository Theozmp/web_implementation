package gr.uoa.di.ted.events.exception;

import org.springframework.http.HttpStatus;

/** Thrown when a destructive action conflicts with existing data (409). */
public class EventConflictException extends AppException {
    public EventConflictException(String message) {
        super(HttpStatus.CONFLICT, "EVENT_CONFLICT", message);
    }
}
