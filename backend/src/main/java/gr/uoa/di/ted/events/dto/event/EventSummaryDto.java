package gr.uoa.di.ted.events.dto.event;

import java.time.LocalDateTime;

/** Lightweight projection used in list views (my-events, public browse). */
public record EventSummaryDto(
        Long          id,
        String        title,
        String        status,
        LocalDateTime startDatetime,
        LocalDateTime endDatetime,
        String        venueName,
        String        city,
        int           capacity,
        int           ticketsSold,
        LocalDateTime createdAt
) {}
