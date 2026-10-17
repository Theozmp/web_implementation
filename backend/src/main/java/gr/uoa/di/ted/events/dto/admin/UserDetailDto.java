package gr.uoa.di.ted.events.dto.admin;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Full user details shown on the admin user-detail page. */
public record UserDetailDto(
        Long          id,
        String        username,
        String        firstName,
        String        lastName,
        String        email,
        String        role,
        String        status,
        boolean       mustChangePassword,
        String        phone,
        String        addressLine,
        String        city,
        String        country,
        BigDecimal    latitude,
        BigDecimal    longitude,
        String        taxId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
