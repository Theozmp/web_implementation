package gr.uoa.di.ted.events.exception;

import org.springframework.http.HttpStatus;

public class MessageNotFoundException extends AppException {
    public MessageNotFoundException(Long id) {
        super(HttpStatus.NOT_FOUND, "MESSAGE_NOT_FOUND", "Message with id " + id + " not found");
    }
}
