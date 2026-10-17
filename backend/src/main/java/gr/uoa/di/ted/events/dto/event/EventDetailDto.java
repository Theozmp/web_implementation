package gr.uoa.di.ted.events.dto.event;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Full event projection returned by GET /events/{id} and POST /events. */
public record EventDetailDto(
        Long               id,
        String             title,
        String             description,
        String             status,
        LocalDateTime      startDatetime,
        LocalDateTime      endDatetime,
        Integer            capacity,
        BigDecimal         latitude,
        BigDecimal         longitude,
        String             venueName,
        String             address,
        String             city,
        String             country,
        Long               organizerId,
        String             organizerUsername,
        List<CategoryDto>  categories,
        List<TicketTypeDto> ticketTypes,
        List<String>       photoUrls,
        int                ticketsSold,
        LocalDateTime      createdAt,
        LocalDateTime      updatedAt
) {}
