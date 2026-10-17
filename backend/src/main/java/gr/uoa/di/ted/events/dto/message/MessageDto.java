package gr.uoa.di.ted.events.dto.message;

import java.time.LocalDateTime;

/** Full message including body, returned by GET /messages/{id}. */
public record MessageDto(
        Long          id,
        Long          senderId,
        String        senderUsername,
        Long          recipientId,
        String        recipientUsername,
        Long          eventId,
        String        eventTitle,
        String        subject,
        String        body,
        LocalDateTime sentAt,
        LocalDateTime readAt
) {}
