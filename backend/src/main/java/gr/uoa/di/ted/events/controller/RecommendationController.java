package gr.uoa.di.ted.events.controller;

import gr.uoa.di.ted.events.dto.event.EventSummaryDto;
import gr.uoa.di.ted.events.recommender.RecommendationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;

/**
 * GET /api/v1/recommendations?limit=10. Ranked PUBLISHED upcoming events for the
 * authenticated user, excluding ones they've already booked. For a cold-start user
 * (not in the training data) it falls back to popularity (most confirmed bookings).
 *
 * Open to any authenticated user (PARTICIPANT, ORGANIZER, ADMIN). Authentication
 * comes from the global anyRequest().authenticated() rule in SecurityConfig, so no
 * @PreAuthorize is needed here.
 */
@RestController
@RequestMapping("/api/v1/recommendations")
@RequiredArgsConstructor
public class RecommendationController {

    private final RecommendationService recommendationService;

    @GetMapping
    public List<EventSummaryDto> getRecommendations(
            @RequestParam(defaultValue = "10") int limit,
            Principal principal) {
        // SecurityConfig guarantees principal is non-null, but guard anyway so
        // tests can call this without a security context.
        String username = principal != null ? principal.getName() : null;
        return recommendationService.getRecommendations(username, Math.min(limit, 50));
    }
}
