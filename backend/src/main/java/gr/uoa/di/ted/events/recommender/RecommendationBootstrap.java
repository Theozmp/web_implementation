package gr.uoa.di.ted.events.recommender;

import gr.uoa.di.ted.events.repository.BookingRepository;
import gr.uoa.di.ted.events.repository.EventViewRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Drives the training lifecycle of the Biased MF recommender. On startup
 * (ApplicationReadyEvent) it loads a saved model from disk, or trains from scratch
 * if none exists. Nightly at 03:00 (@Scheduled) it retrains on the latest data and
 * atomically swaps in the new model so recommendations pick up new bookings.
 *
 * The training matrix comes from two implicit-feedback sources: confirmed bookings
 * (rating 5.0) and authenticated views (rating 1.0). Data is layered seed CSV,
 * then views, then bookings, and each stage can only raise a pair's rating, so a
 * booking always dominates a view and live DB data always overrides the seed.
 * Only one sample per (user, event) pair reaches the trainer.
 *
 * Threading: ApplicationReadyEvent fires on the main thread once the context is
 * up, so repositories are safe to call. The scheduled retrain runs on a Spring
 * TaskScheduler thread; buildTrainingData() opens its own read-only transaction
 * and setModel() swaps the model via an AtomicReference.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RecommendationBootstrap {

    private final RecommendationService recommendationService;
    private final BiasedMfTrainer       trainer;
    private final ModelStore            modelStore;
    private final BiasedMfConfig        config;
    private final DatasetLoader         datasetLoader;
    private final BookingRepository     bookingRepository;
    private final EventViewRepository   viewRepository;

    // startup

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        Optional<BiasedMfModel> saved = modelStore.load();
        if (saved.isPresent()) {
            recommendationService.setModel(saved.get());
            log.info("BMF recommender ready — parameters loaded from disk");
        } else {
            log.info("No saved BMF model — training from current data on startup");
            retrain();
        }
    }

    // nightly retrain

    /** Retrains the model every night at 03:00 server time. */
    @Scheduled(cron = "0 0 3 * * *")
    public void retrain() {
        log.info("BMF nightly retrain started");
        List<TrainingSample> samples = buildTrainingData();
        if (samples.isEmpty()) {
            log.warn("Training data is empty — BMF model NOT updated");
            return;
        }
        BiasedMfModel model = trainer.train(samples, config);
        recommendationService.setModel(model);
        modelStore.save(model);
        log.info("BMF retrain complete on {} samples", samples.size());
    }

    // training data assembly

    /**
     * Builds the implicit-feedback matrix the trainer consumes. The nested
     * userId -> eventId -> rating map de-duplicates (user, event) pairs and lets a
     * booking entry overwrite any earlier view entry for the same pair.
     */
    @Transactional(readOnly = true)
    public List<TrainingSample> buildTrainingData() {
        // userId -> eventId -> rating (de-duplication map)
        Map<Long, Map<Long, Double>> matrix = new HashMap<>();

        // 1. seed CSV data; lowest priority, overridden by live DB data below.
        for (TrainingSample s : datasetLoader.load()) {
            matrix.computeIfAbsent(s.userId(), id -> new HashMap<>())
                  .put(s.eventId(), s.rating());
        }

        // 2. authenticated views, rating 1.0. putIfAbsent so we don't lower an
        //    existing seed value; bookings in step 3 still override it.
        for (Object[] row : viewRepository.findAllAuthenticatedViewPairs()) {
            long userId  = ((Number) row[0]).longValue();
            long eventId = ((Number) row[1]).longValue();
            matrix.computeIfAbsent(userId, id -> new HashMap<>())
                  .putIfAbsent(eventId, 1.0);
        }

        // 3. confirmed bookings, rating 5.0. put() unconditionally, so a booking
        //    wins over any view or seed value for the same pair.
        for (Object[] row : bookingRepository.findAllConfirmedUserEventPairs()) {
            long userId  = ((Number) row[0]).longValue();
            long eventId = ((Number) row[1]).longValue();
            matrix.computeIfAbsent(userId, id -> new HashMap<>())
                  .put(eventId, 5.0);
        }

        // flatten the nested map to a list of TrainingSamples
        List<TrainingSample> samples = new ArrayList<>();
        matrix.forEach((uid, items) ->
                items.forEach((iid, r) -> samples.add(new TrainingSample(uid, iid, r))));

        long distinctUsers = matrix.size();
        long distinctItems = matrix.values().stream().mapToLong(Map::size).sum();
        log.info("Training matrix: {} samples ({} users × avg {} items/user)",
                samples.size(), distinctUsers,
                String.format("%.1f", distinctUsers > 0 ? (double) distinctItems / distinctUsers : 0.0));

        return samples;
    }
}
