package gr.uoa.di.ted.events.dto.booking;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record BookingCreateRequest(
        @NotNull Long ticketTypeId,
        @NotNull @Min(1) Integer numberOfTickets
) {}
