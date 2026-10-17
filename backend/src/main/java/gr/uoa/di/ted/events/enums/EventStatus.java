package gr.uoa.di.ted.events.enums;

/**
 * Lifecycle status of an event.
 * Organizers start with DRAFT; publishing makes it visible to participants.
 */
public enum EventStatus {
    DRAFT,
    PUBLISHED,
    CANCELLED,
    COMPLETED
}
