package gr.uoa.di.ted.events.dto.export;

import java.util.List;

/**
 * One Event element in the export.
 *
 * latitude/longitude are pre-formatted as plain decimal strings so the StAX writer
 * never hits BigDecimal scientific-notation surprises; null means no coordinates
 * recorded, and the writer omits the optional GeoLocation element.
 *
 * eventType: we have no dedicated EventType field, so the first category name is
 * used, falling back to "General".
 */
public record ExportEventDto(
        Long         eventId,
        String       title,
        List<String> categories,
        String       eventType,
        String       venue,
        String       address,
        String       city,
        String       country,
        String       latitude,    // null means omit the GeoLocation element
        String       longitude,
        String       startDateTime,
        String       endDateTime,
        int          capacity,
        List<ExportTicketTypeDto> ticketTypes,
        List<ExportBookingDto>    bookings,
        Long         organizerId,
        String       status,
        String       description,
        List<String> photoUrls    // null or empty means omit the Media element
) {}
