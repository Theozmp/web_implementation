package gr.uoa.di.ted.events.dto.export;

public record ExportBookingDto(
        Long   bookingId,
        Long   attendeeUserId,
        String time,            // ISO-8601 LocalDateTime string
        Long   ticketTypeRef,
        int    numberOfTickets,
        String totalCost,       // plain decimal string
        String bookingStatus    // CONFIRMED | CANCELLED
) {}
