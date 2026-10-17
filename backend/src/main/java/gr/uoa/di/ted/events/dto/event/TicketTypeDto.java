package gr.uoa.di.ted.events.dto.event;

import java.math.BigDecimal;

/**
 * Read-side projection of a ticket tier. sold is the confirmed bookings for this
 * tier; quantity is the remaining seats (decremented by the booking service).
 */
public record TicketTypeDto(
        Long       id,
        String     name,
        BigDecimal price,
        int        quantity,
        int        sold
) {}
