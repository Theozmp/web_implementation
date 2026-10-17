package gr.uoa.di.ted.events.dto.booking;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO returned to the client after a booking is created or when listing bookings.
 *
 * unitPrice is snapshotted from the ticket type at booking time so that
 * historical totals remain correct if the organizer changes prices later.
 */
public record BookingDto(
        Long          id,
        Long          eventId,
        String        eventTitle,
        Long          organizerId,
        String        organizerUsername,
        Long          ticketTypeId,
        String        ticketTypeName,
        BigDecimal    unitPrice,
        int           numberOfTickets,
        BigDecimal    totalCost,
        String        status,
        LocalDateTime bookedAt
) {}
