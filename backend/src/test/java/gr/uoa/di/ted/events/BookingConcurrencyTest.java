package gr.uoa.di.ted.events;

import gr.uoa.di.ted.events.dto.booking.BookingCreateRequest;
import gr.uoa.di.ted.events.dto.event.EventCreateRequest;
import gr.uoa.di.ted.events.dto.event.TicketTypeRequest;
import gr.uoa.di.ted.events.entity.User;
import gr.uoa.di.ted.events.enums.Role;
import gr.uoa.di.ted.events.enums.UserStatus;
import gr.uoa.di.ted.events.exception.TicketUnavailableException;
import gr.uoa.di.ted.events.repository.BookingRepository;
import gr.uoa.di.ted.events.repository.CategoryRepository;
import gr.uoa.di.ted.events.repository.EventRepository;
import gr.uoa.di.ted.events.repository.TicketTypeRepository;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Concurrency test for the pessimistic-locking booking flow. A ticket type has 10
 * seats and 50 threads each try to book 1; exactly 10 should succeed and 40 should
 * get TicketUnavailableException (409).
 *
 * Without locking all 50 would read "10 available", pass the check, and decrement,
 * a classic lost-update race that oversells. With PESSIMISTIC_WRITE
 * (SELECT ... FOR UPDATE) the DB serialises access to the row, so each thread sees
 * the latest committed quantity until it hits 0 and the rest are rejected.
 *
 * @SpringBootTest brings up the full context and @Testcontainers a real MySQL 8
 * (H2 doesn't match MySQL's locking semantics). We call the service directly.
 *
 * Don't put @Transactional on this class: it would share one transaction across
 * threads, the lock would never be contended, and the test would be pointless.
 */
@SpringBootTest
@ActiveProfiles("dev")
@Testcontainers
class BookingConcurrencyTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

    @Autowired BookingService      bookingService;
    @Autowired EventService        eventService;
    @Autowired EventRepository     eventRepository;
    @Autowired BookingRepository   bookingRepository;
    @Autowired TicketTypeRepository ticketTypeRepository;
    @Autowired CategoryRepository  categoryRepository;
    @Autowired UserRepository      userRepository;
    @Autowired PasswordEncoder     passwordEncoder;

    private User organizer;
    private final List<User> participants = new ArrayList<>();
    private Long eventId;
    private Long ticketTypeId;

    private static final int AVAILABLE  = 10;
    private static final int THREADS    = 50;

    @BeforeEach
    void setUp() {
        bookingRepository.deleteAll();
        eventRepository.deleteAll();
        userRepository.findAll().stream()
                .filter(u -> u.getRole() != Role.ADMIN)
                .forEach(userRepository::delete);
        participants.clear();

        organizer = createUser("orgC", "orgC@test.com", Role.ORGANIZER);
        for (int i = 0; i < THREADS; i++) {
            participants.add(createUser("part" + i, "part" + i + "@test.com", Role.PARTICIPANT));
        }

        Long catId = categoryRepository.findAll().get(0).getId();
        EventCreateRequest req = new EventCreateRequest(
                "Concert",
                "Test concert",
                LocalDateTime.now().plusDays(7),
                LocalDateTime.now().plusDays(8),
                AVAILABLE,
                "Arena", "Main St", "Athens", "Greece",
                new BigDecimal("37.97945"), new BigDecimal("23.71622"),
                List.of(catId),
                List.of(new TicketTypeRequest(null, "GA", BigDecimal.TEN, AVAILABLE))
        );
        var detail = eventService.createEvent(req, organizer.getUsername());
        eventService.publishEvent(detail.id(), organizer.getUsername());

        eventId     = detail.id();
        ticketTypeId = detail.ticketTypes().get(0).id();
    }

    @Test
    void booking_withPessimisticLock_exactlyHonorsAvailability()
            throws InterruptedException {

        ExecutorService pool    = Executors.newFixedThreadPool(THREADS);
        CountDownLatch  ready   = new CountDownLatch(THREADS);
        CountDownLatch  start   = new CountDownLatch(1);
        CountDownLatch  done    = new CountDownLatch(THREADS);
        AtomicInteger   success = new AtomicInteger();
        AtomicInteger   conflict = new AtomicInteger();
        AtomicInteger   other   = new AtomicInteger();

        for (int i = 0; i < THREADS; i++) {
            final String username = participants.get(i).getUsername();
            pool.submit(() -> {
                try {
                    ready.countDown();
                    start.await(); // all threads release simultaneously

                    bookingService.createBooking(
                            eventId,
                            new BookingCreateRequest(ticketTypeId, 1),
                            username);
                    success.incrementAndGet();

                } catch (TicketUnavailableException e) {
                    conflict.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    other.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await();          // wait until all threads are queued
        start.countDown();      // fire all threads at once
        boolean finished = done.await(60, TimeUnit.SECONDS);
        pool.shutdown();

        assertThat(finished).as("All threads completed within timeout").isTrue();
        assertThat(other.get()).as("Unexpected exceptions").isZero();
        assertThat(success.get())
                .as("Exactly %d bookings should succeed (pessimistic lock guarantees it)", AVAILABLE)
                .isEqualTo(AVAILABLE);
        assertThat(conflict.get())
                .as("Remaining %d requests should get 409 TICKET_UNAVAILABLE", THREADS - AVAILABLE)
                .isEqualTo(THREADS - AVAILABLE);

        // also check the DB directly: TicketType quantity must be 0
        int remaining = ticketTypeRepository.findById(ticketTypeId)
                .orElseThrow().getQuantity();
        assertThat(remaining).isZero();

        // And exactly AVAILABLE confirmed bookings exist
        long confirmedCount = bookingRepository.findAll().size();
        assertThat(confirmedCount).isEqualTo(AVAILABLE);
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
