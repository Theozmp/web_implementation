package gr.uoa.di.ted.events.controller;

import gr.uoa.di.ted.events.dto.booking.BookingCreateRequest;
import gr.uoa.di.ted.events.dto.booking.BookingDto;
import gr.uoa.di.ted.events.security.UserDetailsImpl;
import gr.uoa.di.ted.events.service.BookingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.SortDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    /**
     * POST /api/v1/events/{eventId}/bookings. Requires PARTICIPANT; organizers
     * also get ROLE_PARTICIPANT from UserDetailsImpl, so they can book too.
     *
     * The service takes a PESSIMISTIC_WRITE lock on the TicketType row before
     * decrementing quantity, so concurrent requests serialise at the DB level
     * instead of being retried in app code.
     */
    @PostMapping("/api/v1/events/{eventId}/bookings")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('PARTICIPANT')")
    public BookingDto createBooking(
            @PathVariable Long eventId,
            @RequestBody @Valid BookingCreateRequest req,
            @AuthenticationPrincipal UserDetailsImpl principal) {
        return bookingService.createBooking(eventId, req, principal.getUsername());
    }

    /** GET /api/v1/bookings/mine. The user's own booking history, newest first. */
    @GetMapping("/api/v1/bookings/mine")
    @PreAuthorize("isAuthenticated()")
    public Page<BookingDto> getMyBookings(
            @PageableDefault(size = 20)
            @SortDefault(sort = "bookedAt", direction = Sort.Direction.DESC)
            Pageable pageable,
            @AuthenticationPrincipal UserDetailsImpl principal) {
        return bookingService.getMyBookings(principal.getUsername(), pageable);
    }

    /**
     * GET /api/v1/events/{eventId}/bookings. All bookings for an event, restricted
     * to the organizer and admins so attendees can't enumerate each other.
     */
    @GetMapping("/api/v1/events/{eventId}/bookings")
    @PreAuthorize("hasAnyRole('ORGANIZER', 'ADMIN')")
    public Page<BookingDto> getEventBookings(
            @PathVariable Long eventId,
            @PageableDefault(size = 20)
            @SortDefault(sort = "bookedAt", direction = Sort.Direction.DESC)
            Pageable pageable,
            @AuthenticationPrincipal UserDetailsImpl principal) {
        return bookingService.getEventBookings(
                eventId, principal.getUsername(), pageable);
    }
}
