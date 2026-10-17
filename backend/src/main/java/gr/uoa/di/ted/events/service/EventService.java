package gr.uoa.di.ted.events.service;

import gr.uoa.di.ted.events.dto.event.*;
import gr.uoa.di.ted.events.entity.*;
import gr.uoa.di.ted.events.enums.BookingStatus;
import gr.uoa.di.ted.events.enums.EventStatus;
import gr.uoa.di.ted.events.enums.Role;
import gr.uoa.di.ted.events.exception.*;
import gr.uoa.di.ted.events.repository.*;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class EventService {

    private final EventRepository      eventRepository;
    private final CategoryRepository   categoryRepository;
    private final BookingRepository    bookingRepository;
    private final EventPhotoRepository photoRepository;
    private final UserRepository       userRepository;
    private final MessageRepository    messageRepository;

    @Value("${app.upload.dir:./uploads}")
    private String uploadDir;

    @Value("${app.upload.max-photos-per-event:5}")
    private int maxPhotosPerEvent;

    // create

    @Transactional
    public EventDetailDto createEvent(EventCreateRequest req, String username) {
        validateDates(req.startDatetime(), req.endDatetime());
        validateFutureStart(req.startDatetime());
        validateCapacityVsTickets(req.capacity(), req.ticketTypes());

        User organizer = requireUserByUsername(username);
        List<Category> categories = loadCategories(req.categoryIds());

        Event event = new Event();
        event.setOrganizer(organizer);
        event.setStatus(EventStatus.DRAFT);
        copyFields(req, event);
        event.getCategories().addAll(categories);
        req.ticketTypes().forEach(ttReq -> event.addTicketType(buildTicketType(ttReq)));

        return toDetail(eventRepository.save(event));
    }

    // my events (paginated)

    @Transactional(readOnly = true)
    public Page<EventSummaryDto> getMyEvents(String username, EventStatus status, Pageable pageable) {
        User organizer = requireUserByUsername(username);

        Specification<Event> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("organizer"), organizer));
            if (status != null) predicates.add(cb.equal(root.get("status"), status));
            return cb.and(predicates.toArray(Predicate[]::new));
        };

        return eventRepository.findAll(spec, pageable).map(this::toSummary);
    }

    // get by id

    @Transactional(readOnly = true)
    public EventDetailDto getEventById(Long id, String callerUsername) {
        Event event = requireEvent(id);

        if (event.getStatus() == EventStatus.DRAFT) {
            // DRAFT events are visible only to the organizer or an admin.
            // Return 404 rather than 403 so we don't leak that the event exists.
            boolean isOrganizer = event.getOrganizer().getUsername().equals(callerUsername);
            boolean isAdmin     = callerUsername != null && isAdmin(callerUsername);
            if (!isOrganizer && !isAdmin) {
                throw new EventNotFoundException(id);
            }
        }

        return toDetail(event);
    }

    // update

    @Transactional
    public EventDetailDto updateEvent(Long id, EventCreateRequest req, String username) {
        Event event = requireEvent(id);
        requireOrganizer(event, username);

        EventStatus status = event.getStatus();
        if (status == EventStatus.CANCELLED || status == EventStatus.COMPLETED) {
            throw new EventOperationException(
                    "Cannot edit a " + status.name().toLowerCase() + " event");
        }

        if (status == EventStatus.PUBLISHED) {
            enforcePublishedEditRules(event, req);
        } else {
            // DRAFT: start date must still be in the future
            validateFutureStart(req.startDatetime());
        }

        validateDates(req.startDatetime(), req.endDatetime());
        validateCapacityVsTickets(req.capacity(), req.ticketTypes());

        List<Category> categories = loadCategories(req.categoryIds());
        copyFields(req, event);
        event.getCategories().clear();
        event.getCategories().addAll(categories);
        updateTicketTypes(event, req.ticketTypes());

        return toDetail(eventRepository.save(event));
    }

    // publish

    @Transactional
    public EventDetailDto publishEvent(Long id, String username) {
        Event event = requireEvent(id);
        requireOrganizer(event, username);

        if (event.getStatus() != EventStatus.DRAFT) {
            throw new EventOperationException("Only DRAFT events can be published");
        }

        event.setStatus(EventStatus.PUBLISHED);
        return toDetail(eventRepository.save(event));
    }

    // cancel

    @Transactional
    public EventDetailDto cancelEvent(Long id, String username) {
        Event event = requireEvent(id);
        requireOrganizer(event, username);

        if (event.getStatus() != EventStatus.PUBLISHED) {
            throw new EventOperationException("Only PUBLISHED events can be cancelled");
        }

        event.setStatus(EventStatus.CANCELLED);
        EventDetailDto result = toDetail(eventRepository.save(event));

        // Notify confirmed attendees via in-app messages. These inserts run in the
        // same transaction as the status change, but a failed insert is caught and
        // logged rather than rethrown, so a messaging hiccup never rolls back the
        // cancellation. (REQUIRES_NEW or @Async would decouple delivery but lose
        // atomicity; we keep it simple and treat messaging as best-effort.)
        List<User> attendees = bookingRepository
                .findDistinctAttendeesByEventIdAndStatus(event.getId(), BookingStatus.CONFIRMED);
        String subject = "Event cancelled: " + event.getTitle();
        String body    = String.format(
                "The event '%s' originally scheduled for %s has been cancelled by the organiser. " +
                "We apologise for any inconvenience. Any booking costs should be refunded per the " +
                "platform's refund policy.",
                event.getTitle(),
                event.getStartDatetime().toLocalDate());
        for (User attendee : attendees) {
            try {
                Message msg = new Message();
                msg.setSender(event.getOrganizer());
                msg.setRecipient(attendee);
                msg.setEvent(event);
                msg.setSubject(subject);
                msg.setBody(body);
                messageRepository.save(msg);
            } catch (Exception ex) {
                // a failed message insert must never roll back the cancellation
                log.error("Could not deliver cancellation notice to user {} for event {}: {}",
                        attendee.getId(), event.getId(), ex.getMessage());
            }
        }

        return result;
    }

    // delete

    @Transactional
    public void deleteEvent(Long id, String username) {
        Event event = requireEvent(id);
        requireOrganizer(event, username);

        if (event.getStatus() != EventStatus.DRAFT) {
            throw new EventOperationException("Only DRAFT events can be deleted");
        }
        if (bookingRepository.existsByEventId(id)) {
            throw new EventConflictException(
                    "Cannot delete an event that already has bookings");
        }

        eventRepository.delete(event);
    }

    // photo upload

    @Transactional
    public PhotoUploadResponse uploadPhoto(Long id, MultipartFile file, String username) {
        Event event = requireEvent(id);
        requireOrganizer(event, username);

        if (photoRepository.countByEventId(id) >= maxPhotosPerEvent) {
            throw new EventOperationException(
                    "Maximum " + maxPhotosPerEvent + " photos allowed per event");
        }
        if (file.isEmpty()) {
            throw new EventOperationException("Uploaded file is empty");
        }

        String url = storeFile(file, id);

        EventPhoto photo = new EventPhoto();
        photo.setEvent(event);
        photo.setUrl(url);
        photoRepository.save(photo);

        return new PhotoUploadResponse(photo.getId(), url);
    }

    // immutability rules for published events

    /**
     * Rules when an organizer edits a PUBLISHED event. The start date can't move
     * into the past (attendees may have calendar entries and it would look like the
     * event already happened), and capacity can't drop below tickets already sold
     * (that would strand attendees holding valid tickets). Everything else (title,
     * description, venue, categories, price) stays freely editable so organisers
     * can fix details without unpublishing.
     */
    private void enforcePublishedEditRules(Event event, EventCreateRequest req) {
        // start date cannot move into the past
        if (req.startDatetime().isBefore(LocalDateTime.now())) {
            throw new EventOperationException(
                    "For a published event, the start date/time cannot be moved into the past");
        }

        // capacity cannot drop below confirmed bookings
        int totalSold = bookingRepository.sumQuantityByEventIdAndStatus(
                event.getId(), BookingStatus.CONFIRMED);
        if (req.capacity() < totalSold) {
            throw new EventOperationException(
                    "Capacity cannot be reduced below tickets already sold (" + totalSold + ")");
        }
    }

    // ticket type reconciliation

    private void updateTicketTypes(Event event, List<TicketTypeRequest> requests) {
        // IDs present in the request: kept and updated
        Set<Long> keepIds = requests.stream()
                .map(TicketTypeRequest::id)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // drop tiers not sent in the request (orphanRemoval issues the DELETE)
        event.getTicketTypes().removeIf(tt -> !keepIds.contains(tt.getId()));

        for (TicketTypeRequest req : requests) {
            if (req.id() != null) {
                // update existing tier
                TicketType tt = event.getTicketTypes().stream()
                        .filter(t -> t.getId().equals(req.id()))
                        .findFirst()
                        .orElseThrow(() -> new EventOperationException(
                                "Ticket type " + req.id() + " does not belong to this event"));

                // never drop quantity below seats already booked for this tier
                int alreadySold = bookingRepository.sumQuantityByTicketTypeIdAndStatus(
                        req.id(), BookingStatus.CONFIRMED);
                if (req.quantity() < alreadySold) {
                    throw new EventOperationException(
                            "Ticket type '" + tt.getName() + "': quantity cannot go below "
                            + alreadySold + " (already booked)");
                }

                tt.setName(req.name());
                tt.setPrice(req.price());
                tt.setQuantity(req.quantity());
            } else {
                // new tier
                event.addTicketType(buildTicketType(req));
            }
        }
    }

    // validation helpers

    private void validateDates(LocalDateTime start, LocalDateTime end) {
        if (!end.isAfter(start)) {
            throw new EventOperationException("End date/time must be after start date/time");
        }
    }

    private void validateFutureStart(LocalDateTime start) {
        if (!start.isAfter(LocalDateTime.now())) {
            throw new EventOperationException("Start date/time must be in the future");
        }
    }

    /** SUM(ticketType.quantity) must not exceed the event's total capacity. */
    private void validateCapacityVsTickets(int capacity, List<TicketTypeRequest> tiers) {
        int sum = tiers.stream().mapToInt(TicketTypeRequest::quantity).sum();
        if (sum > capacity) {
            throw new EventOperationException(
                    "Sum of ticket-type quantities (" + sum
                    + ") exceeds event capacity (" + capacity + ")");
        }
    }

    private List<Category> loadCategories(List<Long> ids) {
        List<Category> categories = categoryRepository.findAllById(ids);
        if (categories.size() != ids.size()) {
            throw new EventOperationException("One or more category IDs are invalid");
        }
        return categories;
    }

    // field copy

    private void copyFields(EventCreateRequest req, Event event) {
        event.setTitle(req.title());
        event.setDescription(req.description());
        event.setStartDatetime(req.startDatetime());
        event.setEndDatetime(req.endDatetime());
        event.setCapacity(req.capacity());
        event.setVenueName(req.venueName());
        event.setAddress(req.address());
        event.setCity(req.city());
        event.setCountry(req.country());
        event.setLatitude(req.latitude());
        event.setLongitude(req.longitude());
    }

    // DTO mapping

    private EventSummaryDto toSummary(Event e) {
        int sold = bookingRepository.sumQuantityByEventIdAndStatus(
                e.getId(), BookingStatus.CONFIRMED);
        return new EventSummaryDto(
                e.getId(), e.getTitle(), e.getStatus().name(),
                e.getStartDatetime(), e.getEndDatetime(),
                e.getVenueName(), e.getCity(),
                e.getCapacity(), sold, e.getCreatedAt());
    }

    private EventDetailDto toDetail(Event e) {
        int sold = bookingRepository.sumQuantityByEventIdAndStatus(
                e.getId(), BookingStatus.CONFIRMED);

        List<CategoryDto> categories = e.getCategories().stream()
                .map(c -> new CategoryDto(c.getId(), c.getName()))
                .toList();

        // N+1: one query per ticket type, fine for typical events (<=10 tiers).
        // Could switch to a GROUP BY query later if it ever matters.
        List<TicketTypeDto> tts = e.getTicketTypes().stream()
                .map(tt -> {
                    int ttSold = bookingRepository.sumQuantityByTicketTypeIdAndStatus(
                            tt.getId(), BookingStatus.CONFIRMED);
                    return new TicketTypeDto(
                            tt.getId(), tt.getName(), tt.getPrice(), tt.getQuantity(), ttSold);
                })
                .toList();

        List<String> photos = photoRepository.findByEventId(e.getId()).stream()
                .map(EventPhoto::getUrl)
                .toList();

        return new EventDetailDto(
                e.getId(), e.getTitle(), e.getDescription(), e.getStatus().name(),
                e.getStartDatetime(), e.getEndDatetime(), e.getCapacity(),
                e.getLatitude(), e.getLongitude(),
                e.getVenueName(), e.getAddress(), e.getCity(), e.getCountry(),
                e.getOrganizer().getId(), e.getOrganizer().getUsername(),
                categories, tts, photos, sold,
                e.getCreatedAt(), e.getUpdatedAt());
    }

    // guard helpers

    private Event requireEvent(Long id) {
        return eventRepository.findById(id).orElseThrow(() -> new EventNotFoundException(id));
    }

    private User requireUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
    }

    private void requireOrganizer(Event event, String username) {
        if (!event.getOrganizer().getUsername().equals(username)) {
            throw new ForbiddenOperationException(
                    "You are not the organizer of this event");
        }
    }

    private boolean isAdmin(String username) {
        return userRepository.findByUsername(username)
                .map(u -> u.getRole() == Role.ADMIN)
                .orElse(false);
    }

    // file storage

    private String storeFile(MultipartFile file, Long eventId) {
        try {
            String originalName = Optional.ofNullable(file.getOriginalFilename()).orElse("photo");
            int dotIdx = originalName.lastIndexOf('.');
            String ext = (dotIdx >= 0) ? originalName.substring(dotIdx + 1).toLowerCase() : "jpg";

            Path dir = Paths.get(uploadDir).resolve("events").resolve(String.valueOf(eventId));
            Files.createDirectories(dir);

            String filename = UUID.randomUUID() + "." + ext;
            Files.copy(file.getInputStream(), dir.resolve(filename),
                    StandardCopyOption.REPLACE_EXISTING);

            return "/uploads/events/" + eventId + "/" + filename;
        } catch (IOException e) {
            throw new EventOperationException("Failed to store photo: " + e.getMessage());
        }
    }

    // ticket type builder

    private TicketType buildTicketType(TicketTypeRequest req) {
        TicketType tt = new TicketType();
        tt.setName(req.name());
        tt.setPrice(req.price());
        tt.setQuantity(req.quantity());
        return tt;
    }
}
