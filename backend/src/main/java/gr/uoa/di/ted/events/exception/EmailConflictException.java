package gr.uoa.di.ted.events.exception;

import org.springframework.http.HttpStatus;

public class EmailConflictException extends AppException {
    public EmailConflictException(String message) {
        super(HttpStatus.CONFLICT, "EMAIL_TAKEN", message);
    }
}
