package gr.uoa.di.ted.events.recommender;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Loads the seed training dataset that bootstraps the Biased MF recommender on
 * first startup, before any live bookings/views have accumulated.
 *
 * Expects the Kaggle "Event Recommendation" {@code event_interest.csv}: a header
 * row plus six comma-separated columns
 * (user, event, invited, timestamp, interested, not_interested). We only use
 * user (col 0), event (col 1, sometimes empty), and interested (col 4, a 0/1
 * flag); the rest are read past but unused.
 *
 * Rating mapping matches live in-app feedback so seed and live data share one
 * scale: interested==1 becomes 5.0 (like a confirmed booking), anything else
 * becomes 1.0 (like a page view). See {@link TrainingSample}.
 *
 * Row handling: the header row (non-numeric first field) is detected and skipped,
 * blank lines and '#' lines are ignored, and a row counts as malformed (skipped)
 * when it has too few columns or a non-numeric user/event id.
 *
 * Never throws; returns an empty list when the dataset is disabled or absent. The
 * recommender works on live DB data alone.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DatasetLoader {

    /** Column indices in event_interest.csv. */
    private static final int COL_USER       = 0;
    private static final int COL_EVENT      = 1;
    private static final int COL_INTERESTED = 4;
    private static final int MIN_COLUMNS    = COL_INTERESTED + 1; // need cols 0..4

    /** Rating for an "interested" row; matches the booking signal. */
    private static final double RATING_INTERESTED = 5.0;
    /** Rating for any other shown row; matches the page-view signal. */
    private static final double RATING_SHOWN      = 1.0;

    private final BiasedMfConfig config;
    private final ResourceLoader resourceLoader;

    /**
     * Reads seed training samples from the configured CSV resource.
     *
     * @return one {@link TrainingSample} per valid interaction row; empty if the
     *         dataset is disabled, the resource is missing, or it is unreadable.
     */
    public List<TrainingSample> load() {
        BiasedMfConfig.Dataset cfg = config.getDataset();

        if (!cfg.isEnabled()) {
            log.info("Recommender dataset disabled (recommender.dataset.enabled=false) — skipping CSV bootstrap");
            return List.of();
        }

        String location = cfg.getPath();
        if (location == null || location.isBlank()) {
            log.info("No recommender dataset path configured — skipping CSV bootstrap");
            return List.of();
        }

        Resource resource = resourceLoader.getResource(location);
        if (!resource.exists()) {
            log.warn("Recommender dataset '{}' not found — CSV bootstrap skipped", location);
            return List.of();
        }

        log.info("Loading recommender dataset from {}", describe(resource, location));

        List<TrainingSample> samples = new ArrayList<>();
        Set<Long> users = new HashSet<>();
        Set<Long> items = new HashSet<>();
        int skipped = 0;
        long lineNum = 0;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {

            String line;
            while ((line = reader.readLine()) != null) {
                lineNum++;
                line = line.strip();
                if (line.isEmpty() || line.startsWith("#")) continue;

                String[] parts = line.split(",", -1);

                // Detect and silently skip the header row (non-numeric first field).
                String first = parts[COL_USER].strip();
                if (!isLong(first)) {
                    // header on line 1 is expected; a non-numeric user id elsewhere is malformed.
                    if (lineNum != 1) {
                        skipped++;
                        // TRACE, not DEBUG: the real dataset has ~843 malformed rows; the
                        // INFO "Skipped K malformed rows" summary is enough at DEBUG level.
                        log.trace("Dataset line {}: non-numeric user id '{}' — skipping", lineNum, first);
                    }
                    continue;
                }

                if (parts.length < MIN_COLUMNS) {
                    skipped++;
                    log.trace("Dataset line {}: expected >= {} columns, got {} — skipping",
                            lineNum, MIN_COLUMNS, parts.length);
                    continue;
                }

                String eventStr = parts[COL_EVENT].strip();
                if (!isLong(eventStr)) {
                    // Empty or non-numeric event id (the dataset has ~843 empty-event rows).
                    skipped++;
                    log.trace("Dataset line {}: missing/invalid event id '{}' — skipping", lineNum, eventStr);
                    continue;
                }

                long userId  = Long.parseLong(first);
                long eventId = Long.parseLong(eventStr);
                double rating = "1".equals(parts[COL_INTERESTED].strip())
                        ? RATING_INTERESTED
                        : RATING_SHOWN;

                samples.add(new TrainingSample(userId, eventId, rating));
                users.add(userId);
                items.add(eventId);
            }
        } catch (IOException e) {
            log.error("Could not read recommender dataset '{}': {}", location, e.getMessage());
            return List.of();
        }

        log.info("Loaded {} interactions across {} users and {} items",
                samples.size(), users.size(), items.size());
        log.info("Skipped {} malformed rows", skipped);
        return samples;
    }

    /** True if {@code s} parses as a Long. */
    private static boolean isLong(String s) {
        if (s == null || s.isEmpty()) return false;
        try {
            Long.parseLong(s);
            return true;
        } catch (NumberFormatException nfe) {
            return false;
        }
    }

    /** Best-effort human-readable resource location for logging. */
    private static String describe(Resource resource, String fallback) {
        try {
            return resource.getURI().toString();
        } catch (IOException e) {
            return fallback;
        }
    }
}
