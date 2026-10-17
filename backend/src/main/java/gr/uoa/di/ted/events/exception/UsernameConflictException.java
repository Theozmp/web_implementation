package gr.uoa.di.ted.events.exception;

import org.springframework.http.HttpStatus;

public class UsernameConflictException extends AppException {
    public UsernameConflictException(String message) {
        super(HttpStatus.CONFLICT, "USERNAME_TAKEN", message);
    }
}
