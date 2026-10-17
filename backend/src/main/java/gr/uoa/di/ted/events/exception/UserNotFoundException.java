package gr.uoa.di.ted.events.exception;

import org.springframework.http.HttpStatus;

public class UserNotFoundException extends AppException {
    public UserNotFoundException(Long id) {
        super(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User with id " + id + " not found");
    }

    public UserNotFoundException(String username) {
        super(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User '" + username + "' not found");
    }
}
