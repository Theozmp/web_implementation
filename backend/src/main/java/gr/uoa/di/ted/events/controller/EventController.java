package gr.uoa.di.ted.events.controller;

import gr.uoa.di.ted.events.dto.event.*;
import gr.uoa.di.ted.events.enums.EventStatus;
import gr.uoa.di.ted.events.security.UserDetailsImpl;
import gr.uoa.di.ted.events.service.EventSearchService;
import gr.uoa.di.ted.events.service.EventService;
import gr.uoa.di.ted.events.service.EventViewService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.SortDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.security.Principal;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService       eventService;
    private final EventSearchService searchService;
    private final EventViewService   viewService;

    // public: discovery

    /**
     * GET /api/v1/events/search. All params are optional and AND together:
     * q (FULLTEXT on title + description, BOOLEAN MODE), category (repeatable, any
     * of the named categories), from/to (ISO date, matched against startDatetime),
     * minPrice/maxPrice (event needs a ticket type in range), city
     * (case-insensitive exact match), and lat/lng/radiusKm (ST_Distance_Sphere).
     *
     * Sorted by startDatetime ascending. Returns PUBLISHED events only unless
     * includeCancelled=true, which also returns CANCELLED ones (e.g. for an
     * attendee's history).
     */
    @GetMapping("/search")
    public Page<EventSummaryDto> searchEvents(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) List<String> category,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng,
            @RequestParam(required = false) Double radiusKm,
            @RequestParam(defaultValue = "false") boolean includeCancelled,
            @PageableDefault(size = 20)
            @SortDefault(sort = "startDatetime", direction = Sort.Direction.ASC)
            Pageable pageable) {

        return searchService.search(
                q, category, from, to, minPrice, maxPrice,
                city, lat, lng, radiusKm, includeCancelled, pageable);
    }

    /**
     * GET /api/v1/events/{id}. Public for PUBLISHED, COMPLETED and CANCELLED
     * events. DRAFT returns 404 unless the caller is the organizer or an admin
     * (403 would leak that the event exists).
     *
     * For an authenticated caller on a PUBLISHED event we record a view
     * fire-and-forget for the recommender; record() doesn't block the response.
     */
    @GetMapping("/{id}")
    public EventDetailDto getEvent(
            @PathVariable Long id,
            Principal principal,
            HttpServletRequest request) {
        String username = (principal != null) ? principal.getName() : null;
        EventDetailDto dto = eventService.getEventById(id, username);
        if ("PUBLISHED".equals(dto.status())) {
            viewService.record(id, username, request.getRemoteAddr());
        }
        return dto;
    }

    // organizer endpoints

    @GetMapping("/mine")
    @PreAuthorize("hasRole('ORGANIZER')")
    public Page<EventSummaryDto> getMyEvents(
            @RequestParam(required = false) EventStatus status,
            @PageableDefault(size = 20)
            @SortDefault(sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable,
            @AuthenticationPrincipal UserDetailsImpl principal) {
        return eventService.getMyEvents(principal.getUsername(), status, pageable);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ORGANIZER')")
    public EventDetailDto createEvent(
            @RequestBody @Valid EventCreateRequest req,
            @AuthenticationPrincipal UserDetailsImpl principal) {
        return eventService.createEvent(req, principal.getUsername());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ORGANIZER')")
    public EventDetailDto updateEvent(
            @PathVariable Long id,
            @RequestBody @Valid EventCreateRequest req,
            @AuthenticationPrincipal UserDetailsImpl principal) {
        return eventService.updateEvent(id, req, principal.getUsername());
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("hasRole('ORGANIZER')")
    public EventDetailDto publishEvent(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetailsImpl principal) {
        return eventService.publishEvent(id, principal.getUsername());
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasRole('ORGANIZER')")
    public EventDetailDto cancelEvent(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetailsImpl principal) {
        return eventService.cancelEvent(id, principal.getUsername());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ORGANIZER')")
    public void deleteEvent(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetailsImpl principal) {
        eventService.deleteEvent(id, principal.getUsername());
    }

    @PostMapping(value = "/{id}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ORGANIZER')")
    public PhotoUploadResponse uploadPhoto(
            @PathVariable Long id,
            @RequestPart("file") MultipartFile file,
            @AuthenticationPrincipal UserDetailsImpl principal) {
        return eventService.uploadPhoto(id, file, principal.getUsername());
    }
}
