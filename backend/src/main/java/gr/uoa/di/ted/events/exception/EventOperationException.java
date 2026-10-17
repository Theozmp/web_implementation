package gr.uoa.di.ted.events.exception;

import org.springframework.http.HttpStatus;

/** Thrown when an event state transition or edit violates a business rule (400). */
public class EventOperationException extends AppException {
    public EventOperationException(String message) {
        super(HttpStatus.BAD_REQUEST, "EVENT_OPERATION_INVALID", message);
    }
}
