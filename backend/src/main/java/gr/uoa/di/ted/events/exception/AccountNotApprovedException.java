package gr.uoa.di.ted.events.exception;

import org.springframework.http.HttpStatus;

/** Thrown when a PENDING user attempts to log in before admin approval. */
public class AccountNotApprovedException extends AppException {
    public AccountNotApprovedException() {
        super(HttpStatus.FORBIDDEN, "ACCOUNT_PENDING",
              "Your account is awaiting admin approval. You will be notified once it is reviewed.");
    }
}
