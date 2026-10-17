package gr.uoa.di.ted.events.repository;

import gr.uoa.di.ted.events.entity.Event;
import gr.uoa.di.ted.events.entity.User;
import gr.uoa.di.ted.events.enums.EventStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * JpaSpecificationExecutor handles the dynamic search filters (city, category,
 * date range, keyword, price, geo-radius) with one Specification instead of many
 * findBy... signatures.
 *
 * Two native queries cover what the Criteria API can't express cleanly.
 * fulltextSearchIds uses MySQL's MATCH...AGAINST (needs the V5 FULLTEXT index;
 * LIKE '%q%' would force a full-table scan) and returns event IDs the service
 * feeds back as id IN (...) so pagination stays correct. geoRadiusIds uses
 * ST_Distance_Sphere to get distance in metres; the same id-based approach keeps
 * the count query simple.
 */
public interface EventRepository extends JpaRepository<Event, Long>,
                                          JpaSpecificationExecutor<Event> {

    List<Event> findByOrganizer(User organizer);

    List<Event> findByStatus(EventStatus status);

    List<Event> findByOrganizerAndStatus(User organizer, EventStatus status);

    /**
     * Event IDs matching the FULLTEXT query. Append '*' for prefix matching in
     * BOOLEAN MODE, e.g. "conc*" matches "concert" and "concerto".
     */
    @Query(value = """
            SELECT id FROM events
            WHERE MATCH(title, description) AGAINST (:q IN BOOLEAN MODE)
            """, nativeQuery = true)
    List<Long> fulltextSearchIds(@Param("q") String q);

    /**
     * Event IDs within radiusM metres of the given point. ST_Distance_Sphere uses
     * the WGS-84 sphere model and is fast on indexed lat/lng columns. Events with
     * NULL coordinates drop out automatically because POINT() returns NULL for them.
     */
    /**
     * All PUBLISHED events starting after {@code now}, ordered by start time. The
     * recommender's candidate pool; we only recommend future events.
     */
    @Query("SELECT e FROM Event e WHERE e.status = 'PUBLISHED' AND e.startDatetime > :now ORDER BY e.startDatetime ASC")
    List<Event> findPublishedUpcoming(@Param("now") java.time.LocalDateTime now);

    @Query(value = """
            SELECT id FROM events
            WHERE latitude IS NOT NULL
              AND longitude IS NOT NULL
              AND ST_Distance_Sphere(
                    POINT(longitude, latitude),
                    POINT(:lng,      :lat)
                  ) <= :radiusM
            """, nativeQuery = true)
    List<Long> geoRadiusIds(
            @Param("lat") double lat,
            @Param("lng") double lng,
            @Param("radiusM") double radiusM);
}
