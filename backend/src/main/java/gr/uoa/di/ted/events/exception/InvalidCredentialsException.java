package gr.uoa.di.ted.events.exception;

import org.springframework.http.HttpStatus;

/** Username or password doesn't match; always maps to 401. */
public class InvalidCredentialsException extends AppException {
    public InvalidCredentialsException() {
        // vague on purpose: don't reveal whether the username or the password was wrong
        super(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid username or password");
    }
}
