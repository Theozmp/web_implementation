package gr.uoa.di.ted.events.dto.message;

import java.time.LocalDateTime;

/** One row in the inbox/sent list; no body, to keep responses small. */
public record MessageSummaryDto(
        Long          id,
        Long          senderId,
        String        senderUsername,
        Long          recipientId,
        String        recipientUsername,
        Long          eventId,
        String        eventTitle,
        String        subject,
        LocalDateTime sentAt,
        boolean       read   // false while readAt == null
) {}
