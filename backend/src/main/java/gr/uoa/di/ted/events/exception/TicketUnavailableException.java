package gr.uoa.di.ted.events.exception;

import org.springframework.http.HttpStatus;

/** Booking request exceeds the remaining ticket supply (409). */
public class TicketUnavailableException extends AppException {

    public TicketUnavailableException(String typeName, int remaining) {
        super(HttpStatus.CONFLICT,
              "TICKET_UNAVAILABLE",
              "Only " + remaining + " ticket(s) remaining for '" + typeName + "'");
    }
}
