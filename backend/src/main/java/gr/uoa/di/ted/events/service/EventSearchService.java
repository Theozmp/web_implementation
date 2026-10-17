package gr.uoa.di.ted.events.service;

import gr.uoa.di.ted.events.dto.event.EventSummaryDto;
import gr.uoa.di.ted.events.entity.Event;
import gr.uoa.di.ted.events.enums.BookingStatus;
import gr.uoa.di.ted.events.enums.EventStatus;
import gr.uoa.di.ted.events.repository.BookingRepository;
import gr.uoa.di.ted.events.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EventSearchService {

    private final EventRepository   eventRepository;
    private final BookingRepository bookingRepository;

    /**
     * Public event discovery with optional filters.
     *
     * Runs in three steps. If q is given, a native FULLTEXT query returns matching
     * event IDs (LIKE '%q%' can't use the FULLTEXT index and would scan the table;
     * the trailing '*' gives prefix matching, e.g. "conc*" matches "concert"). If
     * lat/lng/radiusKm are given, a native ST_Distance_Sphere query returns IDs
     * within the radius (native keeps us from registering a custom Hibernate
     * spatial function). The remaining filters (status, category, date range,
     * price, city) go through one Criteria query, with the pre-computed ID lists
     * injected as IN predicates.
     *
     * Index notes: the (status, start_datetime) composite index covers the default
     * sort, FULLTEXT uses ft_events_title_desc (V5), and the geo query relies on
     * lat/lng being NOT NULL filtered.
     */
    @Transactional(readOnly = true)
    public Page<EventSummaryDto> search(
            String       q,
            List<String> categories,
            LocalDate    from,
            LocalDate    to,
            BigDecimal   minPrice,
            BigDecimal   maxPrice,
            String       city,
            Double       lat,
            Double       lng,
            Double       radiusKm,
            boolean      includeCancelled,
            Pageable     pageable
    ) {
        List<EventStatus> allowed = includeCancelled
                ? List.of(EventStatus.PUBLISHED, EventStatus.CANCELLED)
                : List.of(EventStatus.PUBLISHED);

        // step 1: FULLTEXT IDs (null = filter off, empty = no matches)
        List<Long> textMatchIds = null;
        if (StringUtils.hasText(q)) {
            // trailing '*' is a prefix wildcard in BOOLEAN MODE
            textMatchIds = eventRepository.fulltextSearchIds(q.trim() + "*");
        }

        // step 2: geo-radius IDs
        List<Long> geoMatchIds = null;
        if (lat != null && lng != null && radiusKm != null) {
            geoMatchIds = eventRepository.geoRadiusIds(lat, lng, radiusKm * 1000.0);
        }

        // step 3: combined Specification, paginated fetch
        var spec = EventSearchSpec.build(
                allowed, categories, from, to,
                minPrice, maxPrice, city,
                textMatchIds, geoMatchIds
        );

        return eventRepository.findAll(spec, pageable).map(this::toSummary);
    }

    private EventSummaryDto toSummary(Event e) {
        int sold = bookingRepository.sumQuantityByEventIdAndStatus(
                e.getId(), BookingStatus.CONFIRMED);
        return new EventSummaryDto(
                e.getId(), e.getTitle(), e.getStatus().name(),
                e.getStartDatetime(), e.getEndDatetime(),
                e.getVenueName(), e.getCity(),
                e.getCapacity(), sold, e.getCreatedAt());
    }
}
