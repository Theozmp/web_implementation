package gr.uoa.di.ted.events.repository;

import gr.uoa.di.ted.events.entity.Booking;
import gr.uoa.di.ted.events.entity.Event;
import gr.uoa.di.ted.events.entity.User;
import gr.uoa.di.ted.events.enums.BookingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    List<Booking> findByAttendee(User attendee);

    List<Booking> findByEventId(Long eventId);

    Page<Booking> findByAttendeeOrderByBookedAtDesc(User attendee, Pageable pageable);

    Page<Booking> findByEventOrderByBookedAtDesc(Event event, Pageable pageable);

    boolean existsByEventId(Long eventId);

    boolean existsByEventIdAndAttendee(Long eventId, User attendee);

    // aggregate queries used by EventService

    @Query("SELECT COALESCE(SUM(b.quantity), 0) FROM Booking b " +
           "WHERE b.event.id = :eventId AND b.status = :status")
    int sumQuantityByEventIdAndStatus(
            @Param("eventId") Long eventId,
            @Param("status")  BookingStatus status);

    @Query("SELECT COALESCE(SUM(b.quantity), 0) FROM Booking b " +
           "WHERE b.ticketType.id = :ttId AND b.status = :status")
    int sumQuantityByTicketTypeIdAndStatus(
            @Param("ttId")   Long ttId,
            @Param("status") BookingStatus status);

    // recommendation queries

    /**
     * Distinct event IDs where the user has at least one CONFIRMED booking.
     * RecommendationService uses it to drop already-booked events from the ranking.
     */
    @Query("SELECT DISTINCT b.event.id FROM Booking b " +
           "WHERE b.attendee.id = :userId AND b.status = 'CONFIRMED'")
    List<Long> findConfirmedEventIdsByUserId(@Param("userId") Long userId);

    /**
     * All (attendeeId, eventId) pairs for CONFIRMED bookings. RecommendationBootstrap
     * uses these to build the training matrix (a confirmed booking is rated 5.0).
     */
    @Query("SELECT DISTINCT b.attendee.id, b.event.id FROM Booking b WHERE b.status = 'CONFIRMED'")
    List<Object[]> findAllConfirmedUserEventPairs();

    // cancellation notification query

    /**
     * One User per attendee with at least one confirmed booking for the event.
     * DISTINCT avoids messaging the same attendee once per ticket type.
     */
    @Query("SELECT DISTINCT b.attendee FROM Booking b " +
           "WHERE b.event.id = :eventId AND b.status = :status")
    List<User> findDistinctAttendeesByEventIdAndStatus(
            @Param("eventId") Long eventId,
            @Param("status")  BookingStatus status);
}
