package gr.uoa.di.ted.events.enums;

/**
 * Lifecycle status of a user account.
 * New registrations start as PENDING until an ADMIN approves them.
 */
public enum UserStatus {
    PENDING,
    ACTIVE,
    REJECTED
}
