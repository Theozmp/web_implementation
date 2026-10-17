package gr.uoa.di.ted.events.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * Liveness endpoint for Docker healthchecks and load balancers. Permitted in the
 * security config so it stays callable without a JWT.
 */
@Tag(name = "Health", description = "Application liveness check")
@RestController
@RequestMapping("/api/v1")
public class HealthController {

    @Operation(summary = "Liveness check", description = "Returns HTTP 200 when the application is running.")
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "timestamp", Instant.now().toString(),
                "service", "event-reservation-system"
        ));
    }
}
