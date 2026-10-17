package gr.uoa.di.ted.events.enums;

/**
 * System roles, stored as VARCHAR(30) via @Enumerated(EnumType.STRING).
 * ADMIN manages users and sees all events, ORGANIZER creates and manages their own
 * events, PARTICIPANT browses and books.
 */
public enum Role {
    ADMIN,
    ORGANIZER,
    PARTICIPANT
}
