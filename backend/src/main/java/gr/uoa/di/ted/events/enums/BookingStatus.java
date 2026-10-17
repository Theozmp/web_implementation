package gr.uoa.di.ted.events.enums;

/**
 * Status of a booking record.
 * CANCELLED bookings are kept for audit; the service restores the
 * ticket_type.quantity when a booking is cancelled.
 */
public enum BookingStatus {
    CONFIRMED,
    CANCELLED
}
