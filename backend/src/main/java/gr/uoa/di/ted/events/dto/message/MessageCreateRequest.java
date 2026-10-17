package gr.uoa.di.ted.events.dto.message;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record MessageCreateRequest(
        @NotNull(message = "Recipient is required")
        Long recipientId,

        Long eventId,  // optional; links the message to a specific event

        @NotBlank(message = "Subject is required")
        @Size(max = 255)
        String subject,

        @NotBlank(message = "Body is required")
        @Size(max = 4000)
        String body
) {}
