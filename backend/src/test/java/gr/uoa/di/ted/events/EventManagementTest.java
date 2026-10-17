package gr.uoa.di.ted.events;

import gr.uoa.di.ted.events.dto.event.EventCreateRequest;
import gr.uoa.di.ted.events.dto.event.TicketTypeRequest;
import gr.uoa.di.ted.events.entity.Booking;
import gr.uoa.di.ted.events.entity.Event;
import gr.uoa.di.ted.events.entity.User;
import gr.uoa.di.ted.events.enums.BookingStatus;
import gr.uoa.di.ted.events.enums.Role;
import gr.uoa.di.ted.events.enums.UserStatus;
import gr.uoa.di.ted.events.exception.EventConflictException;
import gr.uoa.di.ted.events.exception.EventOperationException;
import gr.uoa.di.ted.events.exception.ForbiddenOperationException;
import gr.uoa.di.ted.events.repository.BookingRepository;
import gr.uoa.di.ted.events.repository.CategoryRepository;
import gr.uoa.di.ted.events.repository.EventRepository;
import gr.uoa.di.ted.events.repository.UserRepository;
import gr.uoa.di.ted.events.service.EventService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for EventService business rules, against a real MySQL 8 from
 * Testcontainers with Flyway applied, so the service-layer constraints line up with
 * the actual schema.
 *
 * Covers: ticket quantities exceeding capacity (400), deleting an event that has
 * bookings (409), cancelling a DRAFT event (400), and one organizer editing
 * another's event (403).
 */
@SpringBootTest
@ActiveProfiles("dev")
@Testcontainers
class EventManagementTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

    @Autowired EventService      eventService;
    @Autowired EventRepository   eventRepository;
    @Autowired BookingRepository bookingRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired UserRepository    userRepository;
    @Autowired PasswordEncoder   passwordEncoder;

    private User orgA;
    private User orgB;
    private Long category1Id;

    @BeforeEach
    void setUp() {
        // clean up between tests in FK dependency order (bookings, then events, then users)
        bookingRepository.deleteAll();
        eventRepository.deleteAll();
        // remove test users from previous runs; keep the admin seed
        userRepository.findAll().stream()
                .filter(u -> u.getRole() != Role.ADMIN)
                .forEach(userRepository::delete);

        orgA = createOrganizer("orgA", "orgA@test.com");
        orgB = createOrganizer("orgB", "orgB@test.com");
        category1Id = categoryRepository.findAll().get(0).getId();
    }

    // test 1: capacity violation

    @Test
    void createEvent_whenTicketQuantitiesExceedCapacity_returns400() {
        EventCreateRequest req = buildRequest(
                100,   // capacity
                List.of(
                        new TicketTypeRequest(null, "General", BigDecimal.valueOf(10), 80),
                        new TicketTypeRequest(null, "VIP",     BigDecimal.valueOf(50), 30)
                        // 80 + 30 = 110 > 100
                )
        );

        assertThatThrownBy(() -> eventService.createEvent(req, orgA.getUsername()))
                .isInstanceOf(EventOperationException.class)
                .hasMessageContaining("exceeds event capacity");
    }

    // test 2: delete after booking

    @Test
    void deleteEvent_whenBookingsExist_returns409() {
        // create and publish a valid event
        EventCreateRequest req = buildRequest(50,
                List.of(new TicketTypeRequest(null, "General", BigDecimal.TEN, 50)));
        Long eventId = eventService.createEvent(req, orgA.getUsername()).id();
        eventService.publishEvent(eventId, orgA.getUsername());

        // create a booking straight through the repository
        Event event = eventRepository.findById(eventId).orElseThrow();
        Booking booking = new Booking();
        booking.setEvent(event);
        booking.setAttendee(orgB);
        booking.setTicketType(event.getTicketTypes().get(0));
        booking.setQuantity(1);
        booking.setTotalPrice(BigDecimal.TEN);
        booking.setStatus(BookingStatus.CONFIRMED);
        bookingRepository.save(booking);

        // deleting a non-DRAFT event is blocked by EventOperationException
        assertThatThrownBy(() -> eventService.deleteEvent(eventId, orgA.getUsername()))
                .isInstanceOf(EventOperationException.class)
                .hasMessageContaining("DRAFT");

        // a DRAFT event with a booking should hit the booking-existence guard
        // (new draft plus an injected booking)
        Long draftId = eventService.createEvent(
                buildRequest(50, List.of(
                        new TicketTypeRequest(null, "GA", BigDecimal.TEN, 50))),
                orgA.getUsername()).id();
        Event draftEvent = eventRepository.findById(draftId).orElseThrow();
        Booking draftBooking = new Booking();
        draftBooking.setEvent(draftEvent);
        draftBooking.setAttendee(orgB);
        draftBooking.setTicketType(draftEvent.getTicketTypes().get(0));
        draftBooking.setQuantity(1);
        draftBooking.setTotalPrice(BigDecimal.TEN);
        draftBooking.setStatus(BookingStatus.CONFIRMED);
        bookingRepository.save(draftBooking);

        assertThatThrownBy(() -> eventService.deleteEvent(draftId, orgA.getUsername()))
                .isInstanceOf(EventConflictException.class)
                .hasMessageContaining("bookings");
    }

    // test 3: cancel on DRAFT

    @Test
    void cancelEvent_whenStatusIsDraft_returns400() {
        Long draftId = eventService.createEvent(
                buildRequest(50, List.of(
                        new TicketTypeRequest(null, "GA", BigDecimal.TEN, 50))),
                orgA.getUsername()).id();

        assertThatThrownBy(() -> eventService.cancelEvent(draftId, orgA.getUsername()))
                .isInstanceOf(EventOperationException.class)
                .hasMessageContaining("PUBLISHED");
    }

    // test 4: organizer A cannot edit organizer B's event

    @Test
    void updateEvent_whenCallerIsNotOrganizer_returns403() {
        Long eventId = eventService.createEvent(
                buildRequest(50, List.of(
                        new TicketTypeRequest(null, "GA", BigDecimal.TEN, 50))),
                orgA.getUsername()).id();

        EventCreateRequest updateReq = buildRequest(50, List.of(
                new TicketTypeRequest(null, "GA", BigDecimal.TEN, 50)));

        assertThatThrownBy(() -> eventService.updateEvent(eventId, updateReq, orgB.getUsername()))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    // fixtures

    private User createOrganizer(String username, String email) {
        User u = new User();
        u.setUsername(username);
        u.setEmail(email);
        u.setPasswordHash(passwordEncoder.encode("Test@1234"));
        u.setFirstName("Test");
        u.setLastName("User");
        u.setRole(Role.ORGANIZER);
        u.setStatus(UserStatus.ACTIVE);
        u.setTaxId("094259216");
        return userRepository.save(u);
    }

    private EventCreateRequest buildRequest(int capacity, List<TicketTypeRequest> tiers) {
        return new EventCreateRequest(
                "Test Event",
                "Description",
                LocalDateTime.now().plusDays(7),
                LocalDateTime.now().plusDays(8),
                capacity,
                "Test Venue", "Test St", "Athens", "Greece",
                new BigDecimal("37.97945"), new BigDecimal("23.71622"),
                List.of(category1Id),
                tiers
        );
    }
}
