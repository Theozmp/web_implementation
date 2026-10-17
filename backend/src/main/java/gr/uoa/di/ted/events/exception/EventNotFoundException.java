package gr.uoa.di.ted.events.exception;

import org.springframework.http.HttpStatus;

public class EventNotFoundException extends AppException {
    public EventNotFoundException(Long id) {
        super(HttpStatus.NOT_FOUND, "EVENT_NOT_FOUND", "Event with id " + id + " not found");
    }
}
