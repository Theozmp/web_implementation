package gr.uoa.di.ted.events.dto.auth;

import java.time.Instant;

/**
 * Returned by POST /api/v1/auth/login on success. The nested UserSummary carries
 * just enough for the Angular shell (nav-bar name, role-based menu visibility,
 * mustChangePassword redirect); full profile details come from GET /users/me.
 */
public record AuthResponse(
        String accessToken,
        Instant expiresAt,
        UserSummary user
) {
    public record UserSummary(
            Long    id,
            String  username,
            String  firstName,
            String  lastName,
            String  email,
            String  role,
            boolean mustChangePassword
    ) {}
}
