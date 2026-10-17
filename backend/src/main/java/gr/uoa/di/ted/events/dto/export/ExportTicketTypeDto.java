package gr.uoa.di.ted.events.dto.export;

/**
 * quantity = total originally declared (available + sold).
 * available = remaining seats = TicketType.quantity (decremented on booking).
 */
public record ExportTicketTypeDto(
        Long   ticketTypeId,
        String name,
        String price,      // plain decimal string, no scientific notation
        int    quantity,   // total declared at creation time
        int    available   // seats still bookable
) {}
