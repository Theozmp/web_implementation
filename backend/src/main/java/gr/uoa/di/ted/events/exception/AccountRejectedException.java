package gr.uoa.di.ted.events.exception;

import org.springframework.http.HttpStatus;

/** Thrown when a REJECTED user attempts to log in. */
public class AccountRejectedException extends AppException {
    public AccountRejectedException() {
        super(HttpStatus.FORBIDDEN, "ACCOUNT_REJECTED",
              "Your account registration has been rejected. Please contact the administrator.");
    }
}
