package gr.uoa.di.ted.events.dto.event;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * One pricing tier inside an event create/update request.
 * id is null when adding a new tier; non-null when editing an existing one.
 */
public record TicketTypeRequest(
        Long id,
        @NotBlank @Size(max = 100) String name,
        @NotNull @DecimalMin("0.00") BigDecimal price,
        @NotNull @Positive Integer quantity
) {}
