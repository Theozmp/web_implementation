package gr.uoa.di.ted.events;

import gr.uoa.di.ted.events.dto.booking.BookingCreateRequest;
import gr.uoa.di.ted.events.dto.event.EventCreateRequest;
import gr.uoa.di.ted.events.dto.event.TicketTypeRequest;
import gr.uoa.di.ted.events.entity.User;
import gr.uoa.di.ted.events.enums.Role;
import gr.uoa.di.ted.events.enums.UserStatus;
import gr.uoa.di.ted.events.repository.BookingRepository;
import gr.uoa.di.ted.events.repository.CategoryRepository;
import gr.uoa.di.ted.events.repository.EventRepository;
import gr.uoa.di.ted.events.repository.MessageRepository;
import gr.uoa.di.ted.events.repository.UserRepository;
import gr.uoa.di.ted.events.service.BookingService;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Acceptance test: cancelling an event with 3 bookings from 3 different users sends
 * each of them a notification message.
 *
 * An organiser publishes a 5-seat event, three participants each book one seat, the
 * organiser cancels, and we assert exactly 3 Message rows exist, one per participant,
 * from the organiser, referencing the cancelled event.
 *
 * Uses @SpringBootTest + Testcontainers because the notification runs in the same
 * transaction as the cancellation; a real MySQL container confirms the FK
 * constraints hold and the rows actually commit with cancelEvent.
 */
@SpringBootTest
@ActiveProfiles("dev")
@Testcontainers
class CancellationNotificationTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

    @Autowired EventService      eventService;
    @Autowired BookingService    bookingService;
    @Autowired MessageRepository messageRepository;
    @Autowired BookingRepository bookingRepository;
    @Autowired EventRepository   eventRepository;
    @Autowired CategoryRepository categoryRepository;
    @Autowired UserRepository    userRepository;
    @Autowired PasswordEncoder   passwordEncoder;

    private User organizer;
    private User participant1, participant2, participant3;
    private Long eventId;

    @BeforeEach
    void setUp() {
        messageRepository.deleteAll();
        bookingRepository.deleteAll();
        eventRepository.deleteAll();
        userRepository.findAll().stream()
                .filter(u -> u.getRole() != Role.ADMIN)
                .forEach(userRepository::delete);

        organizer    = createUser("org_notif",   "org_notif@test.com",   Role.ORGANIZER);
        participant1 = createUser("part_notif1", "part_notif1@test.com", Role.PARTICIPANT);
        participant2 = createUser("part_notif2", "part_notif2@test.com", Role.PARTICIPANT);
        participant3 = createUser("part_notif3", "part_notif3@test.com", Role.PARTICIPANT);

        Long catId = categoryRepository.findAll().get(0).getId();
        var req = new EventCreateRequest(
                "Notification Test Concert", "A test event",
                LocalDateTime.now().plusDays(7), LocalDateTime.now().plusDays(8),
                5, "Arena", "Main St", "Athens", "Greece",
                new BigDecimal("37.97945"), new BigDecimal("23.71622"),
                List.of(catId),
                List.of(new TicketTypeRequest(null, "GA", BigDecimal.TEN, 5))
        );
        var detail = eventService.createEvent(req, organizer.getUsername());
        eventService.publishEvent(detail.id(), organizer.getUsername());
        eventId = detail.id();

        Long ttId = detail.ticketTypes().get(0).id();
        bookingService.createBooking(eventId, new BookingCreateRequest(ttId, 1), participant1.getUsername());
        bookingService.createBooking(eventId, new BookingCreateRequest(ttId, 1), participant2.getUsername());
        bookingService.createBooking(eventId, new BookingCreateRequest(ttId, 1), participant3.getUsername());
    }

    @Test
    void cancelEvent_sendsOneMessageToEachAttendee() {
        eventService.cancelEvent(eventId, organizer.getUsername());

        var messages = messageRepository.findAll();

        assertThat(messages)
                .as("Exactly one notification message per distinct attendee")
                .hasSize(3);

        assertThat(messages)
                .allSatisfy(m -> {
                    assertThat(m.getSender().getId())
                            .as("Message sender is the organiser")
                            .isEqualTo(organizer.getId());
                    assertThat(m.getEvent().getId())
                            .as("Message is linked to the cancelled event")
                            .isEqualTo(eventId);
                    assertThat(m.getSubject())
                            .as("Subject references the event title")
                            .contains("Notification Test Concert");
                });

        var recipientIds = messages.stream()
                .map(m -> m.getRecipient().getId())
                .toList();
        assertThat(recipientIds)
                .as("One message addressed to each of the 3 participants")
                .containsExactlyInAnyOrder(
                        participant1.getId(),
                        participant2.getId(),
                        participant3.getId());
    }

    private User createUser(String username, String email, Role role) {
        User u = new User();
        u.setUsername(username);
        u.setEmail(email);
        u.setPasswordHash(passwordEncoder.encode("Test@1234"));
        u.setFirstName("Test");
        u.setLastName("User");
        u.setRole(role);
        u.setStatus(UserStatus.ACTIVE);
        if (role == Role.ORGANIZER) u.setTaxId("094259216");
        return userRepository.save(u);
    }
}
