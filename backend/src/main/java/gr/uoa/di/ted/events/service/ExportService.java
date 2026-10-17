package gr.uoa.di.ted.events.service;

import gr.uoa.di.ted.events.dto.export.*;
import gr.uoa.di.ted.events.entity.Booking;
import gr.uoa.di.ted.events.entity.Category;
import gr.uoa.di.ted.events.entity.Event;
import gr.uoa.di.ted.events.entity.EventPhoto;
import gr.uoa.di.ted.events.enums.BookingStatus;
import gr.uoa.di.ted.events.repository.BookingRepository;
import gr.uoa.di.ted.events.repository.EventPhotoRepository;
import gr.uoa.di.ted.events.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExportService {

    private final EventRepository      eventRepository;
    private final BookingRepository    bookingRepository;
    private final EventPhotoRepository photoRepository;

    /**
     * Loads all events (any status) and maps them to flat export DTOs.
     *
     * N+1: two extra queries per event (bookings + photos), fine for a typical
     * admin export of a few hundred events; batch-loading both up front would be
     * the scale fix. Runs in one read-only transaction so lazy associations
     * (organizer, categories, ticket types) load without LazyInitialization errors.
     */
    @Transactional(readOnly = true)
    public ExportEventsWrapper buildExport() {
        List<Event> events = eventRepository.findAll(Sort.by(Sort.Direction.ASC, "id"));
        List<ExportEventDto> dtos = events.stream()
                .map(this::toDto)
                .toList();
        return new ExportEventsWrapper(dtos);
    }

    // mapping

    private ExportEventDto toDto(Event event) {
        List<Booking>    bookings = bookingRepository.findByEventId(event.getId());
        List<EventPhoto> photos   = photoRepository.findByEventId(event.getId());

        // confirmed tickets sold per ticket type, used to recover the original
        // declared quantity (available + sold = total declared at creation).
        Map<Long, Integer> soldByType = bookings.stream()
                .filter(b -> b.getStatus() == BookingStatus.CONFIRMED)
                .collect(Collectors.groupingBy(
                        b -> b.getTicketType().getId(),
                        Collectors.summingInt(Booking::getQuantity)));

        List<String> categoryNames = event.getCategories().stream()
                .map(Category::getName)
                .toList();

        // we have no dedicated event-type field; the first category is the closest
        // match, "General" for uncategorised events.
        String eventType = categoryNames.isEmpty() ? "General" : categoryNames.get(0);

        List<ExportTicketTypeDto> ttDtos = event.getTicketTypes().stream()
                .map(tt -> {
                    int sold      = soldByType.getOrDefault(tt.getId(), 0);
                    int available = tt.getQuantity();               // remaining seats
                    int total     = available + sold;               // originally declared
                    return new ExportTicketTypeDto(
                            tt.getId(),
                            tt.getName(),
                            tt.getPrice().toPlainString(),
                            total,
                            available);
                })
                .toList();

        List<ExportBookingDto> bDtos = bookings.stream()
                .map(b -> new ExportBookingDto(
                        b.getId(),
                        b.getAttendee().getId(),
                        b.getBookedAt().toString(),
                        b.getTicketType().getId(),
                        b.getQuantity(),
                        b.getTotalPrice().toPlainString(),
                        b.getStatus().name()))
                .toList();

        return new ExportEventDto(
                event.getId(),
                event.getTitle(),
                categoryNames,
                eventType,
                event.getVenueName(),
                event.getAddress(),
                event.getCity(),
                event.getCountry(),
                event.getLatitude()  != null ? event.getLatitude().toPlainString()  : null,
                event.getLongitude() != null ? event.getLongitude().toPlainString() : null,
                event.getStartDatetime().toString(),
                event.getEndDatetime().toString(),
                event.getCapacity(),
                ttDtos,
                bDtos,
                event.getOrganizer().getId(),
                event.getStatus().name(),
                event.getDescription(),
                photos.stream().map(EventPhoto::getUrl).toList());
    }
}
