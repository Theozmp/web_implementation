package gr.uoa.di.ted.events.service;

import gr.uoa.di.ted.events.entity.Event;
import gr.uoa.di.ted.events.entity.TicketType;
import gr.uoa.di.ted.events.enums.EventStatus;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds a JPA {@link Specification} for the public event search endpoint.
 *
 * Only handles filters expressible via the Criteria API. Filters that need native
 * SQL (FULLTEXT, ST_Distance_Sphere) are run in {@link EventSearchService}; their
 * results come back as {@code textMatchIds}/{@code geoMatchIds} and become a plain
 * {@code id IN (...)} predicate here.
 *
 * The category filter uses a correlated sub-query rather than a JOIN: a JOIN would
 * return one event once per matching category, duplicating rows and breaking the
 * pagination count query.
 */
class EventSearchSpec {

    static Specification<Event> build(
            List<EventStatus> allowedStatuses,
            List<String> categories,
            LocalDate from,
            LocalDate to,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            String city,
            List<Long> textMatchIds,
            List<Long> geoMatchIds
    ) {
        return (root, query, cb) -> {
            List<Predicate> preds = new ArrayList<>();

            // status
            preds.add(root.get("status").in(allowedStatuses));

            // categories: sub-query keeps the outer query free of duplicate rows
            if (!CollectionUtils.isEmpty(categories)) {
                Subquery<Long> catSub = query.subquery(Long.class);
                Root<Event> catRoot = catSub.from(Event.class);
                catSub.select(catRoot.get("id"));
                // JOIN inside the sub-query is fine; the outer query stays clean
                var catJoin = catRoot.join("categories");
                catSub.where(
                        cb.equal(catRoot.get("id"), root.get("id")),
                        catJoin.<String>get("name").in(categories)
                );
                preds.add(cb.exists(catSub));
            }

            // date range (on startDatetime)
            if (from != null) {
                preds.add(cb.greaterThanOrEqualTo(
                        root.get("startDatetime"), from.atStartOfDay()));
            }
            if (to != null) {
                preds.add(cb.lessThanOrEqualTo(
                        root.get("startDatetime"), to.atTime(23, 59, 59)));
            }

            // city: case-insensitive exact match
            if (StringUtils.hasText(city)) {
                preds.add(cb.equal(
                        cb.lower(root.get("city")), city.strip().toLowerCase()));
            }

            // price range: EXISTS a ticket_type priced within [min, max]
            if (minPrice != null || maxPrice != null) {
                Subquery<Long> ttSub = query.subquery(Long.class);
                Root<TicketType> tt = ttSub.from(TicketType.class);
                ttSub.select(tt.get("id"));
                List<Predicate> pp = new ArrayList<>();
                pp.add(cb.equal(tt.get("event").get("id"), root.get("id")));
                if (minPrice != null)
                    pp.add(cb.greaterThanOrEqualTo(tt.get("price"), minPrice));
                if (maxPrice != null)
                    pp.add(cb.lessThanOrEqualTo(tt.get("price"), maxPrice));
                ttSub.where(pp.toArray(new Predicate[0]));
                preds.add(cb.exists(ttSub));
            }

            // FULLTEXT results: native query pre-computed, injected as IDs
            if (textMatchIds != null) {
                // empty list means nothing matched the text query, so nothing passes
                preds.add(textMatchIds.isEmpty()
                        ? cb.disjunction()
                        : root.get("id").in(textMatchIds));
            }

            // geo-radius results (ST_Distance_Sphere), same pattern
            if (geoMatchIds != null) {
                preds.add(geoMatchIds.isEmpty()
                        ? cb.disjunction()
                        : root.get("id").in(geoMatchIds));
            }

            return cb.and(preds.toArray(new Predicate[0]));
        };
    }

    private EventSearchSpec() {}
}
