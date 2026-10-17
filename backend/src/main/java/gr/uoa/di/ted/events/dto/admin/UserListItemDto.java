package gr.uoa.di.ted.events.dto.admin;

import java.time.LocalDateTime;

/** Projection used for the admin user-management table (one row per user). */
public record UserListItemDto(
        Long          id,
        String        username,
        String        firstName,
        String        lastName,
        String        email,
        String        role,
        String        status,
        LocalDateTime createdAt
) {}
