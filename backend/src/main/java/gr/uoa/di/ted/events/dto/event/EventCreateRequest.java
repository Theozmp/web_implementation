package gr.uoa.di.ted.events.dto.event;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record EventCreateRequest(
        @NotBlank @Size(max = 200) String title,
        String description,

        @NotNull LocalDateTime startDatetime,
        @NotNull LocalDateTime endDatetime,

        @NotNull @Positive Integer capacity,

        // Venue / location
        @Size(max = 255) String venueName,
        @Size(max = 255) String address,
        @Size(max = 100) String city,
        @Size(max = 100) String country,
        @DecimalMin("-90.0")  @DecimalMax("90.0")  BigDecimal latitude,
        @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal longitude,

        @NotEmpty List<Long> categoryIds,

        @NotEmpty @Valid List<TicketTypeRequest> ticketTypes
) {}
