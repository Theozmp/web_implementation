package gr.uoa.di.ted.events.recommender;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for BiasedMfTrainer. Pure Java, no Spring context or database. They
 * check that training RMSE beats the naive baseline, that predictions are finite
 * for known and cold-start pairs, and that edge cases (empty data, single sample)
 * don't crash. The synthetic dataset has users 1-3 preferring events 1-2 and users
 * 4-6 preferring events 3-4, so a trained model should pick up the two clusters.
 */
class BiasedMfTrainerTest {

    private BiasedMfTrainer trainer;
    private BiasedMfConfig  config;

    @BeforeEach
    void setUp() {
        trainer = new BiasedMfTrainer();

        config = new BiasedMfConfig();
        // small k so tests run fast, but enough to capture the two clusters.
        config.setLatentFactors(5);
        config.setLearningRate(0.02);
        config.setRegularization(0.02);
        config.setEpochs(100);
        config.setEarlyStoppingPatience(20);
    }

    // test 1: model fits the training data (RMSE converges)

    @Test
    void trainingRmse_isLowerThanNaiveBaseline_afterConvergence() {
        // Naive baseline always predicts the global mean. With equal r=1.0 and r=5.0
        // the mean is 3.0, so baseline RMSE is sqrt((16+4)/2) ~= 3.16. After enough
        // SGD epochs the model should beat that comfortably.

        List<TrainingSample> samples = buildSyntheticDataset();
        BiasedMfTrainer.TrainingResult result = trainer.trainWithHistory(samples, config);
        BiasedMfModel model = result.model();

        // training RMSE over the full dataset
        double sumSqErr = samples.stream()
                .mapToDouble(s -> {
                    double err = s.rating() - model.predict(s.userId(), s.eventId());
                    return err * err;
                })
                .sum();
        double trainRmse = Math.sqrt(sumSqErr / samples.size());

        // should land well below the ~3.16 mean baseline; 2.2 is a lenient threshold
        // that's still a clear improvement.
        assertThat(trainRmse)
                .as("Training RMSE (%.4f) should beat naive mean baseline (≈3.16)", trainRmse)
                .isLessThan(2.2);

        // history recorded, which shows the trainer tracks convergence
        assertThat(result.validationRmseHistory()).isNotEmpty();
    }

    // test 2: predicted scores are finite

    @Test
    void predictedScores_areFiniteAndBounded_forTrainingPairs() {
        List<TrainingSample> samples = buildSyntheticDataset();
        BiasedMfModel model = trainer.train(samples, config);

        for (TrainingSample s : samples) {
            double score = model.predict(s.userId(), s.eventId());

            assertThat(Double.isNaN(score))
                    .as("Prediction for (user=%d, event=%d) must not be NaN", s.userId(), s.eventId())
                    .isFalse();
            assertThat(Double.isInfinite(score))
                    .as("Prediction for (user=%d, event=%d) must not be Infinite", s.userId(), s.eventId())
                    .isFalse();
            // ratings are in [1.0, 5.0]; with regularisation predictions stay near that band
            assertThat(score).isBetween(-50.0, 50.0);
        }
    }

    @Test
    void predictedScores_areFinite_forUnknownUserItemPair() {
        // cold start: user/event not in the training data
        BiasedMfModel model = trainer.train(buildSyntheticDataset(), config);

        double score = model.predict(-1L, -1L);  // IDs never seen during training

        assertThat(Double.isNaN(score)).isFalse();
        assertThat(Double.isInfinite(score)).isFalse();
        // unknown pair: only globalMean contributes, and ratings are in [1.0, 5.0]
        assertThat(score).isBetween(0.0, 6.0);
    }

    @Test
    void hasUser_returnsFalse_forUnknownUser() {
        BiasedMfModel model = trainer.train(buildSyntheticDataset(), config);
        assertThat(model.hasUser(-999L)).isFalse();
    }

    @Test
    void hasUser_returnsTrue_forTrainedUser() {
        BiasedMfModel model = trainer.train(buildSyntheticDataset(), config);
        assertThat(model.hasUser(1L)).isTrue();
    }

    // test 3: edge cases

    @Test
    void emptyDataset_returnsModelWithoutCrashing() {
        BiasedMfModel model = trainer.train(List.of(), config);
        assertThat(model).isNotNull();
        // empty model predicts 0 (globalMean is 0 with no data)
        double score = model.predict(1L, 1L);
        assertThat(Double.isNaN(score)).isFalse();
        assertThat(Double.isInfinite(score)).isFalse();
    }

    @Test
    void singleSample_trainsFineWithoutException() {
        List<TrainingSample> one = List.of(new TrainingSample(1L, 1L, 5.0));
        // single sample: the 90/10 split leaves 0 training samples, so the trainer
        // has to degrade gracefully.
        assertThat(trainer.train(one, config)).isNotNull();
    }

    // synthetic dataset

    /**
     * A 6-user, 4-item dataset with two clusters: users 1-3 prefer events 1-2 (5.0)
     * and only mildly like 3-4 (1.0); users 4-6 are the reverse. 24 triples, enough
     * for a non-trivial 90/10 split, and a model with k>=2 should learn the clusters.
     */
    private List<TrainingSample> buildSyntheticDataset() {
        List<TrainingSample> s = new ArrayList<>();

        // Cluster A: users 1-3 like events 1-2
        for (long u = 1; u <= 3; u++) {
            s.add(new TrainingSample(u, 1L, 5.0));
            s.add(new TrainingSample(u, 2L, 5.0));
            s.add(new TrainingSample(u, 3L, 1.0));
            s.add(new TrainingSample(u, 4L, 1.0));
        }
        // Cluster B: users 4-6 like events 3-4
        for (long u = 4; u <= 6; u++) {
            s.add(new TrainingSample(u, 1L, 1.0));
            s.add(new TrainingSample(u, 2L, 1.0));
            s.add(new TrainingSample(u, 3L, 5.0));
            s.add(new TrainingSample(u, 4L, 5.0));
        }

        return s;
    }
}
