package gr.uoa.di.ted.events.recommender;

import gr.uoa.di.ted.events.dto.event.EventSummaryDto;
import gr.uoa.di.ted.events.entity.Event;
import gr.uoa.di.ted.events.entity.User;
import gr.uoa.di.ted.events.enums.BookingStatus;
import gr.uoa.di.ted.events.enums.EventStatus;
import gr.uoa.di.ted.events.repository.BookingRepository;
import gr.uoa.di.ted.events.repository.EventRepository;
import gr.uoa.di.ted.events.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Unit tests for RecommendationService. Mockito stubs the repositories, so no
 * Spring context or database. Focuses on the cold-start popularity fallback and the
 * already-booked filtering.
 */
@ExtendWith(MockitoExtension.class)
class RecommendationServiceTest {

    @Mock EventRepository   eventRepository;
    @Mock BookingRepository bookingRepository;
    @Mock UserRepository    userRepository;

    @InjectMocks RecommendationService service;

    private User user;
    private Event eventA;  // more popular (more tickets sold)
    private Event eventB;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
        user.setUsername("alice");

        eventA = makeEvent(10L, "Rock Concert");
        eventB = makeEvent(20L, "Jazz Evening");
    }

    // cold-start returns popularity order

    @Test
    void coldStart_noModel_returnsPopularityRanking() {
        // no model set, so modelRef is null and we take the cold-start path
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(bookingRepository.findConfirmedEventIdsByUserId(1L)).thenReturn(List.of());
        when(eventRepository.findPublishedUpcoming(any())).thenReturn(List.of(eventA, eventB));
        // eventA has more bookings
        when(bookingRepository.sumQuantityByEventIdAndStatus(eq(10L), eq(BookingStatus.CONFIRMED)))
                .thenReturn(100);
        when(bookingRepository.sumQuantityByEventIdAndStatus(eq(20L), eq(BookingStatus.CONFIRMED)))
                .thenReturn(40);

        List<EventSummaryDto> results = service.getRecommendations("alice", 10);

        // most popular event ranks first
        assertThat(results).hasSize(2);
        assertThat(results.get(0).id()).isEqualTo(10L);
        assertThat(results.get(1).id()).isEqualTo(20L);
    }

    @Test
    void coldStart_knownUser_noFactors_returnsPopularityRanking() {
        // model is set, but this user isn't in it
        BiasedMfModel model = new BiasedMfModel(5, 3.0,
                java.util.Map.of(), java.util.Map.of(),
                java.util.Map.of(), java.util.Map.of());
        service.setModel(model);  // user 1L absent from model.userFactors

        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(bookingRepository.findConfirmedEventIdsByUserId(1L)).thenReturn(List.of());
        when(eventRepository.findPublishedUpcoming(any())).thenReturn(List.of(eventA, eventB));
        when(bookingRepository.sumQuantityByEventIdAndStatus(eq(10L), eq(BookingStatus.CONFIRMED)))
                .thenReturn(5);
        when(bookingRepository.sumQuantityByEventIdAndStatus(eq(20L), eq(BookingStatus.CONFIRMED)))
                .thenReturn(50);

        List<EventSummaryDto> results = service.getRecommendations("alice", 10);

        // eventB has more bookings here, so it ranks first
        assertThat(results.get(0).id()).isEqualTo(20L);
    }

    // already-booked events are excluded

    @Test
    void coldStart_excludesAlreadyBookedEvents() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        // user already booked eventA (id=10)
        when(bookingRepository.findConfirmedEventIdsByUserId(1L)).thenReturn(List.of(10L));
        when(eventRepository.findPublishedUpcoming(any())).thenReturn(List.of(eventA, eventB));
        when(bookingRepository.sumQuantityByEventIdAndStatus(eq(20L), eq(BookingStatus.CONFIRMED)))
                .thenReturn(30);

        List<EventSummaryDto> results = service.getRecommendations("alice", 10);

        // eventA (id=10) must be excluded since the user already booked it
        assertThat(results).hasSize(1);
        assertThat(results.get(0).id()).isEqualTo(20L);
    }

    // limit is respected

    @Test
    void coldStart_respectsLimit() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(bookingRepository.findConfirmedEventIdsByUserId(1L)).thenReturn(List.of());
        when(eventRepository.findPublishedUpcoming(any())).thenReturn(List.of(eventA, eventB));
        when(bookingRepository.sumQuantityByEventIdAndStatus(eq(10L), eq(BookingStatus.CONFIRMED)))
                .thenReturn(10);
        when(bookingRepository.sumQuantityByEventIdAndStatus(eq(20L), eq(BookingStatus.CONFIRMED)))
                .thenReturn(20);

        List<EventSummaryDto> results = service.getRecommendations("alice", 1);

        assertThat(results).hasSize(1);
    }

    // unknown username falls back to popularity

    @Test
    void unknownUsername_returnsPopularityFallback() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());
        when(eventRepository.findPublishedUpcoming(any())).thenReturn(List.of(eventA));
        when(bookingRepository.sumQuantityByEventIdAndStatus(eq(10L), eq(BookingStatus.CONFIRMED)))
                .thenReturn(7);

        List<EventSummaryDto> results = service.getRecommendations("ghost", 10);

        assertThat(results).hasSize(1);
    }

    // helper

    private Event makeEvent(long id, String title) {
        Event e = new Event();
        // set the private id by reflection; Event uses @GeneratedValue with no id setter.
        try {
            var idField = Event.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(e, id);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
        e.setTitle(title);
        e.setStatus(EventStatus.PUBLISHED);
        e.setStartDatetime(LocalDateTime.now().plusDays(7));
        e.setEndDatetime(LocalDateTime.now().plusDays(7).plusHours(2));
        e.setCapacity(500);
        return e;
    }
}
