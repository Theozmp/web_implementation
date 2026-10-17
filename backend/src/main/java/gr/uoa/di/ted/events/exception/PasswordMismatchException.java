package gr.uoa.di.ted.events.exception;

import org.springframework.http.HttpStatus;

public class PasswordMismatchException extends AppException {
    public PasswordMismatchException(String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "PASSWORD_MISMATCH", message);
    }
}
