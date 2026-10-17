package gr.uoa.di.ted.events.service;

import gr.uoa.di.ted.events.dto.booking.BookingCreateRequest;
import gr.uoa.di.ted.events.dto.booking.BookingDto;
import gr.uoa.di.ted.events.entity.Booking;
import gr.uoa.di.ted.events.entity.Event;
import gr.uoa.di.ted.events.entity.TicketType;
import gr.uoa.di.ted.events.entity.User;
import gr.uoa.di.ted.events.enums.BookingStatus;
import gr.uoa.di.ted.events.enums.EventStatus;
import gr.uoa.di.ted.events.enums.Role;
import gr.uoa.di.ted.events.exception.*;
import gr.uoa.di.ted.events.repository.BookingRepository;
import gr.uoa.di.ted.events.repository.EventRepository;
import gr.uoa.di.ted.events.repository.TicketTypeRepository;
import gr.uoa.di.ted.events.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class BookingService {

    private final BookingRepository     bookingRepository;
    private final EventRepository       eventRepository;
    private final TicketTypeRepository  ticketTypeRepository;
    private final UserRepository        userRepository;

    /**
     * Creates a confirmed booking for the requesting user.
     *
     * Concurrency: a PESSIMISTIC_WRITE (SELECT ... FOR UPDATE) lock on the
     * TicketType row serialises concurrent bookings for the same tier. Only one
     * transaction holds the lock at a time; the rest wait at the DB level, which
     * makes the check-then-decrement atomic without app-level retries. See
     * TicketTypeRepository.findByIdWithLock() for the comparison with optimistic
     * locking.
     */
    @Transactional
    public BookingDto createBooking(Long eventId,
                                    BookingCreateRequest req,
                                    String username) {
        // 1. load the event; no lock needed, we only check its status
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException(eventId));

        if (event.getStatus() != EventStatus.PUBLISHED) {
            throw new EventOperationException(
                    "Bookings are only accepted for PUBLISHED events");
        }

        // 2. lock the ticket type row; concurrent transactions for the same row
        //    block here until this one commits or rolls back.
        TicketType tt = ticketTypeRepository
                .findByIdWithLock(req.ticketTypeId())
                .orElseThrow(() -> new EventOperationException(
                        "Ticket type not found: " + req.ticketTypeId()));

        if (!tt.getEvent().getId().equals(eventId)) {
            throw new EventOperationException(
                    "Ticket type does not belong to event " + eventId);
        }

        // 3. check remaining supply (quantity is remaining seats, decremented per booking)
        if (tt.getQuantity() < req.numberOfTickets()) {
            throw new TicketUnavailableException(tt.getName(), tt.getQuantity());
        }

        // 4. decrement the available count inside the locked transaction
        tt.setQuantity(tt.getQuantity() - req.numberOfTickets());
        ticketTypeRepository.save(tt);

        // 5. snapshot the unit price now so historical totals survive later price changes
        User attendee = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "User not found: " + username));

        BigDecimal unitPrice = tt.getPrice();
        BigDecimal totalCost = unitPrice.multiply(
                BigDecimal.valueOf(req.numberOfTickets()));

        Booking booking = new Booking();
        booking.setEvent(event);
        booking.setAttendee(attendee);
        booking.setTicketType(tt);
        booking.setQuantity(req.numberOfTickets());
        booking.setTotalPrice(totalCost);
        booking.setStatus(BookingStatus.CONFIRMED);

        return toDto(bookingRepository.save(booking));
    }

    // my bookings

    @Transactional(readOnly = true)
    public Page<BookingDto> getMyBookings(String username, Pageable pageable) {
        User user = requireUser(username);
        return bookingRepository
                .findByAttendeeOrderByBookedAtDesc(user, pageable)
                .map(this::toDto);
    }

    // event bookings (organizer / admin)

    @Transactional(readOnly = true)
    public Page<BookingDto> getEventBookings(Long eventId,
                                             String callerUsername,
                                             Pageable pageable) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EventNotFoundException(eventId));

        User caller = requireUser(callerUsername);
        boolean isOrganizer = event.getOrganizer().getUsername().equals(callerUsername);
        boolean isAdmin     = caller.getRole() == Role.ADMIN;

        if (!isOrganizer && !isAdmin) {
            throw new ForbiddenOperationException(
                    "Only the event organizer or an admin can view bookings");
        }

        return bookingRepository
                .findByEventOrderByBookedAtDesc(event, pageable)
                .map(this::toDto);
    }

    // mapping

    private BookingDto toDto(Booking b) {
        return new BookingDto(
                b.getId(),
                b.getEvent().getId(),
                b.getEvent().getTitle(),
                b.getEvent().getOrganizer().getId(),
                b.getEvent().getOrganizer().getUsername(),
                b.getTicketType().getId(),
                b.getTicketType().getName(),
                b.getTicketType().getPrice(),    // unit price at time of booking
                b.getQuantity(),
                b.getTotalPrice(),
                b.getStatus().name(),
                b.getBookedAt());
    }

    private User requireUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException(
                        "User not found: " + username));
    }
}
