package gr.uoa.di.ted.events.recommender;

import gr.uoa.di.ted.events.dto.event.EventSummaryDto;
import gr.uoa.di.ted.events.entity.Event;
import gr.uoa.di.ted.events.entity.User;
import gr.uoa.di.ted.events.enums.BookingStatus;
import gr.uoa.di.ted.events.repository.BookingRepository;
import gr.uoa.di.ted.events.repository.EventRepository;
import gr.uoa.di.ted.events.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Serves personalized event recommendations using the Biased MF model.
 *
 * Cold start: when the requesting user has no signal in the training data (no
 * bookings or views before the last retrain), we fall back to popularity, i.e.
 * the most-booked upcoming events. It's a sensible, interpretable prior; random
 * ordering would tell the user nothing.
 *
 * Thread safety: the active model lives in an AtomicReference. Bootstrap calls
 * setModel() at startup and once per daily retrain; HTTP threads read it
 * concurrently via a single volatile get(), so no locking is needed and the old
 * model keeps serving until the reference is swapped.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationService {

    private final EventRepository   eventRepository;
    private final BookingRepository bookingRepository;
    private final UserRepository    userRepository;

    /** Atomic reference so concurrent HTTP reads never see a partially-updated model. */
    private final AtomicReference<BiasedMfModel> modelRef = new AtomicReference<>();

    /** Called by RecommendationBootstrap after training to swap in the new model. */
    public void setModel(BiasedMfModel model) {
        modelRef.set(model);
        log.info("Active BMF model updated (k={}, users={}, items={})",
                model.getK(),
                model.getUserFactors() != null ? model.getUserFactors().size() : 0,
                model.getItemFactors() != null ? model.getItemFactors().size() : 0);
    }

    /**
     * Top {@code limit} published upcoming events for the user, ranked by predicted
     * BMF score (or by popularity for cold-start users). Events the user has already
     * confirmed a booking for are filtered out.
     *
     * @param username authenticated user's username; null falls back to popularity
     * @param limit    maximum number of events to return
     */
    @Transactional(readOnly = true)
    public List<EventSummaryDto> getRecommendations(String username, int limit) {
        if (username == null) {
            return popularityFallback(limit, null);
        }

        Optional<User> userOpt = userRepository.findByUsername(username);
        if (userOpt.isEmpty()) {
            return popularityFallback(limit, null);
        }

        User user = userOpt.get();
        BiasedMfModel model = modelRef.get();

        // cold start: no model yet, or the user wasn't in the last training snapshot
        // (registered since, or browsed but never booked). Fall back to popularity.
        if (model == null || !model.hasUser(user.getId())) {
            log.debug("Cold-start for user {} — falling back to popularity", username);
            return popularityFallback(limit, user.getId());
        }

        // events already confirmed by the user; excluded from candidates
        Set<Long> alreadyBooked = new HashSet<>(
                bookingRepository.findConfirmedEventIdsByUserId(user.getId()));

        return scoredAndRanked(user.getId(), alreadyBooked, model, limit);
    }

    // private helpers

    /**
     * Scores all published upcoming events for the user with the BMF model, drops
     * already-booked events, and returns the top {@code limit}.
     */
    private List<EventSummaryDto> scoredAndRanked(
            Long userId, Set<Long> alreadyBooked, BiasedMfModel model, int limit) {

        List<Event> candidates = eventRepository.findPublishedUpcoming(LocalDateTime.now());

        // pair each event with its predicted score and sold count; computing
        // soldCount here saves toDto() an extra query per event.
        record ScoredEvent(Event event, double score, int soldCount) {}

        return candidates.stream()
                .filter(e -> !alreadyBooked.contains(e.getId()))
                .map(e -> new ScoredEvent(
                        e,
                        model.predict(userId, e.getId()),  // BMF predicted score
                        bookingRepository.sumQuantityByEventIdAndStatus(
                                e.getId(), BookingStatus.CONFIRMED)))
                .sorted(Comparator.comparingDouble(ScoredEvent::score).reversed())
                .limit(limit)
                .map(se -> toDto(se.event(), se.soldCount()))
                .toList();
    }

    /**
     * Popularity fallback: the most-booked published upcoming events, sorted by
     * confirmed ticket sales descending then start date ascending. Events the user
     * has already booked are excluded when excludeUserId is non-null.
     *
     * Used when no model has been trained yet, or the user isn't in the training
     * data (cold start). Without user signal, proven demand is the best prior.
     */
    private List<EventSummaryDto> popularityFallback(int limit, Long excludeUserId) {
        Set<Long> alreadyBooked = (excludeUserId != null)
                ? new HashSet<>(bookingRepository.findConfirmedEventIdsByUserId(excludeUserId))
                : Set.of();

        record EventWithCount(Event event, int soldCount) {}

        return eventRepository.findPublishedUpcoming(LocalDateTime.now()).stream()
                .filter(e -> !alreadyBooked.contains(e.getId()))
                .map(e -> new EventWithCount(
                        e,
                        bookingRepository.sumQuantityByEventIdAndStatus(
                                e.getId(), BookingStatus.CONFIRMED)))
                .sorted(Comparator.comparingInt(EventWithCount::soldCount).reversed())
                .limit(limit)
                .map(ewc -> toDto(ewc.event(), ewc.soldCount()))
                .toList();
    }

    private EventSummaryDto toDto(Event e, int soldCount) {
        return new EventSummaryDto(
                e.getId(), e.getTitle(), e.getStatus().name(),
                e.getStartDatetime(), e.getEndDatetime(),
                e.getVenueName(), e.getCity(),
                e.getCapacity(), soldCount, e.getCreatedAt());
    }
}
