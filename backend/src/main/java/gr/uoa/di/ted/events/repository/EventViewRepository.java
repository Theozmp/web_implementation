package gr.uoa.di.ted.events.repository;

import gr.uoa.di.ted.events.entity.EventView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface EventViewRepository extends JpaRepository<EventView, Long> {

    List<EventView> findByEventId(Long eventId);

    // distinct events a user has viewed; input to the recommender
    @Query("SELECT DISTINCT v.event.id FROM EventView v WHERE v.viewer.id = :userId")
    List<Long> findViewedEventIdsByUser(@Param("userId") Long userId);

    long countByEventId(Long eventId);

    /**
     * All (viewerId, eventId) pairs for authenticated (non-anonymous) views.
     * RecommendationBootstrap uses these; a view without a booking is rated 1.0.
     */
    @Query("SELECT v.viewer.id, v.event.id FROM EventView v WHERE v.viewer IS NOT NULL")
    List<Object[]> findAllAuthenticatedViewPairs();
}
