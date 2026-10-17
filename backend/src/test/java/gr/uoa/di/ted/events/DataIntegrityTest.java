package gr.uoa.di.ted.events;

import gr.uoa.di.ted.events.entity.*;
import gr.uoa.di.ted.events.enums.Role;
import gr.uoa.di.ted.events.enums.UserStatus;
import gr.uoa.di.ted.events.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for data-layer invariants, against a real MySQL 8 container so
 * Flyway runs exactly as in production, ddl-auto: validate confirms the schema
 * matches the entities, and cascade behaviour (SQL and JPA) is exercised on a real
 * engine.
 *
 * @ServiceConnection on the static container overrides the datasource settings from
 * application-dev.yml automatically, no @DynamicPropertySource needed. The container
 * is static so it's shared across methods and we pay the MySQL startup cost once.
 */
@SpringBootTest
@ActiveProfiles("dev")
@Testcontainers
class DataIntegrityTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

    @Autowired private UserRepository        userRepository;
    @Autowired private EventRepository       eventRepository;
    @Autowired private TicketTypeRepository  ticketTypeRepository;
    @Autowired private EventPhotoRepository  eventPhotoRepository;
    @Autowired private BookingRepository     bookingRepository;
    @Autowired private EventViewRepository   eventViewRepository;
    @Autowired private MessageRepository     messageRepository;

    /**
     * Clean up test-created rows before each test in reverse FK dependency order.
     * Flyway-seeded rows (admin user, categories) are kept.
     */
    @BeforeEach
    void cleanup() {
        bookingRepository.deleteAll();
        eventViewRepository.deleteAll();
        messageRepository.deleteAll();
        // deleting events cascades to ticket_types via JPA (cascade=ALL) and to
        // event_photos/event_categories via SQL ON DELETE CASCADE.
        eventRepository.deleteAll();
        // remove only test-created users; keep the admin seeded by V2.
        userRepository.findAll().stream()
                .filter(u -> u.getRole() != Role.ADMIN)
                .forEach(userRepository::delete);
    }

    // test 1

    /**
     * Checks two cascade mechanisms together: JPA cascade=ALL on Event.ticketTypes
     * deletes the TicketType with its parent, and SQL ON DELETE CASCADE on
     * event_photos.event_id deletes the EventPhoto at the DB level even though Event
     * has no @OneToMany for photos (left off to keep the aggregate root lean).
     */
    @Test
    void deleteEvent_removesTicketTypesAndPhotos() {
        // organizer
        User organizer = buildUser("testOrganizer", "org@test.com", Role.ORGANIZER);
        organizer = userRepository.save(organizer);

        // event with one ticket type, saved through the aggregate root
        Event event = buildEvent("Test Concert", organizer, 200);
        TicketType general = buildTicketType("General Admission", new BigDecimal("15.00"), 200);
        event.addTicketType(general); // keeps TicketType.event in sync
        event = eventRepository.save(event); // cascades save to TicketType

        long eventId      = event.getId();
        long ticketTypeId = event.getTicketTypes().get(0).getId();

        // photo, saved independently (no JPA cascade path from Event)
        EventPhoto photo = new EventPhoto();
        photo.setEvent(event);
        photo.setUrl("https://example.com/concert.jpg");
        photo = eventPhotoRepository.save(photo);
        long photoId = photo.getId();

        eventRepository.deleteById(eventId);

        // JPA cascade removed the ticket type
        assertThat(ticketTypeRepository.findById(ticketTypeId))
                .as("TicketType must be deleted by JPA cascade (Event.ticketTypes, cascade=ALL)")
                .isEmpty();

        // SQL ON DELETE CASCADE removed the photo
        assertThat(eventPhotoRepository.findById(photoId))
                .as("EventPhoto must be deleted by SQL ON DELETE CASCADE on event_photos.event_id")
                .isEmpty();
    }

    // test 2 (placeholder)

    /**
     * Placeholder for the capacity-ceiling invariant. It's @Disabled because
     * "SUM(ticket_type.quantity) must not exceed event.capacity" is a multi-row
     * aggregate that can't be a simple DB constraint without a serialisable
     * transaction or trigger, both of which would hurt booking throughput. The
     * check lives in the service layer instead.
     *
     * Plan: load the event, SELECT SUM(quantity) for it, throw
     * CapacityExceededException (409) if SUM + newQuantity exceeds capacity, else
     * persist the new TicketType. Re-enable this test once that's implemented.
     */
    @Test
    @Disabled("Capacity constraint is enforced in TicketTypeService (Phase 5). "
            + "Enable and complete this test when the service layer is in place.")
    void ticketTypeQuantitySumCannotExceedEventCapacity() {
        // TODO: remove @Disabled and implement the assertion:
        //   Event event = ... (capacity = 100);
        //   save TicketType(quantity = 60);
        //   assertThrows(CapacityExceededException.class, () ->
        //       ticketTypeService.create(event, TicketTypeRequest(quantity = 50)));
    }

    // helpers

    private User buildUser(String username, String email, Role role) {
        User u = new User();
        u.setUsername(username);
        u.setEmail(email);
        u.setPasswordHash("$2b$12$placeholderHashNotUsedInTests");
        u.setFirstName("Test");
        u.setLastName("User");
        u.setRole(role);
        u.setStatus(UserStatus.ACTIVE);
        return u;
    }

    private Event buildEvent(String title, User organizer, int capacity) {
        Event e = new Event();
        e.setTitle(title);
        e.setOrganizer(organizer);
        e.setStartDatetime(LocalDateTime.now().plusDays(30));
        e.setEndDatetime(LocalDateTime.now().plusDays(30).plusHours(3));
        e.setCapacity(capacity);
        return e;
    }

    private TicketType buildTicketType(String name, BigDecimal price, int quantity) {
        TicketType tt = new TicketType();
        tt.setName(name);
        tt.setPrice(price);
        tt.setQuantity(quantity);
        return tt;
    }
}
