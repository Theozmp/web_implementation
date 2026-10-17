package gr.uoa.di.ted.events.recommender;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

/**
 * Persists and restores the trained BiasedMfModel as a JSON file.
 *
 * JSON rather than Java serialisation: .ser is tied to the exact class structure,
 * so adding a field to BiasedMfModel would corrupt old files. JSON is
 * self-describing, readable, and tolerant of minor schema changes.
 *
 * Long map keys round-trip fine: Jackson writes them as JSON string keys and its
 * StdKeyDeserializer turns them back into Long on read.
 *
 * No synchronisation: save()/load() are only called from RecommendationBootstrap
 * (single-threaded startup plus the 03:00 scheduled retrain).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ModelStore {

    private final ObjectMapper   objectMapper;
    private final BiasedMfConfig config;

    /**
     * Writes the model parameters to disk at the configured path.
     * Creates parent directories if they do not exist.
     * Failures are logged, not re-thrown; a missing persist isn't fatal.
     */
    public void save(BiasedMfModel model) {
        try {
            Path path = Paths.get(config.getModelPath());
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            objectMapper.writeValue(path.toFile(), model);
            log.info("BMF model saved to {} (k={}, users={}, items={})",
                    path.toAbsolutePath(),
                    model.getK(),
                    model.getUserFactors().size(),
                    model.getItemFactors().size());
        } catch (IOException e) {
            log.error("Failed to persist BMF model: {}", e.getMessage());
        }
    }

    /**
     * Loads a previously saved model from disk.
     * Returns Optional.empty() if the file does not exist or cannot be parsed,
     * so the caller can fall back to retraining from scratch.
     */
    public Optional<BiasedMfModel> load() {
        Path path = Paths.get(config.getModelPath());
        if (!Files.exists(path)) {
            log.info("No saved BMF model found at {} — will train from scratch",
                    path.toAbsolutePath());
            return Optional.empty();
        }
        try {
            BiasedMfModel model = objectMapper.readValue(path.toFile(), BiasedMfModel.class);
            log.info("BMF model loaded from {} (k={}, users={}, items={})",
                    path.toAbsolutePath(),
                    model.getK(),
                    model.getUserFactors() != null ? model.getUserFactors().size() : 0,
                    model.getItemFactors() != null ? model.getItemFactors().size() : 0);
            return Optional.of(model);
        } catch (IOException e) {
            log.error("Failed to load BMF model from {}: {} — will retrain",
                    path.toAbsolutePath(), e.getMessage());
            return Optional.empty();
        }
    }
}
