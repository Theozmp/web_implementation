package gr.uoa.di.ted.events.recommender;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Trains a Biased Matrix Factorization model with stochastic gradient descent.
 *
 * Model: pred = globalMean + userBias + itemBias + dot(p_u, q_i). See BiasedMfModel.
 *
 * Loss is the regularised squared error over all observed (user, item, rating)
 * triples: sum (r - pred)^2 + lambda * (||p_u||^2 + ||q_i||^2 + b_u^2 + b_i^2).
 * The L2 term pushes rarely-updated parameters toward zero to limit overfitting.
 *
 * Per-triple SGD update (e = r - pred, eta = learning rate, lambda = reg):
 *   b_u += eta * (e - lambda * b_u)
 *   b_i += eta * (e - lambda * b_i)
 *   p_u += eta * (e * q_i - lambda * p_u)   (dimension by dimension)
 *   q_i += eta * (e * p_u - lambda * q_i)   (uses the OLD p_u)
 * We copy p_u before updating it because q_i's gradient needs p_u from before its
 * update (standard Jacobi-style SGD). Using the just-updated p_u would couple the
 * two updates; both converge, but this matches the loss above.
 *
 * Procedure: shuffle and hold out 10% for validation, compute the global mean from
 * the rest, init biases to 0 and factors to small N(0, 0.1) noise (so the k factors
 * differ instead of staying identical), then run epochs of shuffled SGD, tracking
 * validation RMSE and stopping early after earlyStoppingPatience epochs without
 * improvement.
 */
@Slf4j
@Component
public class BiasedMfTrainer {

    /**
     * Training result: the fitted model plus a per-epoch validation RMSE history
     * (useful for inspecting convergence in unit tests without re-running training).
     */
    public record TrainingResult(BiasedMfModel model, List<Double> validationRmseHistory) {}

    // public API

    /** Trains a model and returns only the model (drops the RMSE history). */
    public BiasedMfModel train(List<TrainingSample> samples, BiasedMfConfig cfg) {
        return trainWithHistory(samples, cfg).model();
    }

    /**
     * Trains a model and returns it plus the per-epoch validation RMSEs.
     * Unit tests use this overload to assert convergence.
     */
    public TrainingResult trainWithHistory(List<TrainingSample> samples, BiasedMfConfig cfg) {
        if (samples.isEmpty()) {
            log.warn("No training samples — returning untrained model with globalMean=0");
            return new TrainingResult(emptyModel(cfg.getLatentFactors()), List.of());
        }

        // step 1: shuffle and 90/10 train/validation split.
        // fixed seed so restarts on the same data are reproducible.
        Random rng = new Random(42L);
        List<TrainingSample> shuffled = new ArrayList<>(samples);
        Collections.shuffle(shuffled, rng);

        int splitIdx = (int) (shuffled.size() * 0.9);
        // copy into independent lists so trainSet can be reshuffled each epoch.
        List<TrainingSample> trainSet = new ArrayList<>(shuffled.subList(0, splitIdx));
        List<TrainingSample> valSet   = new ArrayList<>(shuffled.subList(splitIdx, shuffled.size()));

        log.info("BMF training: {} train samples, {} validation samples",
                trainSet.size(), valSet.size());

        // step 2: global mean from the training set. It anchors every prediction;
        // without it the biases and factors would have to absorb the overall level.
        double globalMean = trainSet.stream()
                .mapToDouble(TrainingSample::rating)
                .average()
                .orElse(0.0);

        // step 3: init parameters.
        int k = cfg.getLatentFactors();

        // all user/item IDs in the training set. Every valSet ID also appears here
        // because we split on samples, not on users/items.
        Set<Long> userIds = trainSet.stream()
                .map(s -> s.userId()).collect(Collectors.toSet());
        Set<Long> itemIds = trainSet.stream()
                .map(s -> s.eventId()).collect(Collectors.toSet());

        Map<Long, Double>   userBias    = new HashMap<>(userIds.size());
        Map<Long, Double>   itemBias    = new HashMap<>(itemIds.size());
        Map<Long, double[]> userFactors = new HashMap<>(userIds.size());
        Map<Long, double[]> itemFactors = new HashMap<>(itemIds.size());

        // biases start at 0; the global mean already explains most of the rating.
        for (Long uid : userIds) {
            userBias.put(uid, 0.0);
            // small N(0, 0.1) values so no factor dominates at the start.
            userFactors.put(uid, randomVector(k, rng));
        }
        for (Long iid : itemIds) {
            itemBias.put(iid, 0.0);
            itemFactors.put(iid, randomVector(k, rng));
        }

        // step 4: SGD training loop.
        double lr     = cfg.getLearningRate();
        double lambda = cfg.getRegularization();

        double       bestValRmse  = Double.MAX_VALUE;
        int          patience     = 0;
        List<Double> rmseHistory  = new ArrayList<>();

        for (int epoch = 1; epoch <= cfg.getEpochs(); epoch++) {

            // 4a) reshuffle each epoch; SGD descends more smoothly when
            // consecutive samples aren't correlated.
            Collections.shuffle(trainSet, rng);

            // 4b) SGD update for each training triple
            for (TrainingSample s : trainSet) {
                Long uid = s.userId();
                Long iid = s.eventId();

                double[] pu = userFactors.get(uid);
                double[] qi = itemFactors.get(iid);

                double pred = globalMean
                        + userBias.get(uid)
                        + itemBias.get(iid)
                        + dot(pu, qi);

                // error; positive means we under-predicted, negative over-predicted.
                double e = s.rating() - pred;

                // user bias update; the lambda*bu term decays it toward zero.
                double bu = userBias.get(uid);
                userBias.put(uid, bu + lr * (e - lambda * bu));

                // item bias update
                double bi = itemBias.get(iid);
                itemBias.put(iid, bi + lr * (e - lambda * bi));

                // copy p_u before updating it: q_i's gradient needs the old p_u
                // (Jacobi step). Using the just-updated p_u would couple the two.
                double[] puOld = Arrays.copyOf(pu, k);

                // user factor update; e*q_i[f] is the fit term, lambda*p_u[f] the reg.
                for (int f = 0; f < k; f++) {
                    pu[f] += lr * (e * qi[f] - lambda * pu[f]);
                }

                // item factor update, using the saved old p_u.
                for (int f = 0; f < k; f++) {
                    qi[f] += lr * (e * puOld[f] - lambda * qi[f]);
                }
            }

            // 4c) validation RMSE
            if (!valSet.isEmpty()) {
                double valRmse = computeRmse(
                        valSet, globalMean, userBias, itemBias, userFactors, itemFactors, k);
                rmseHistory.add(valRmse);
                log.debug("Epoch {}/{}: validation RMSE = {}", epoch, cfg.getEpochs(),
                        String.format("%.4f", valRmse));

                // 4d) early stopping
                if (valRmse < bestValRmse) {
                    bestValRmse = valRmse;
                    patience    = 0;
                } else {
                    patience++;
                    if (patience >= cfg.getEarlyStoppingPatience()) {
                        log.info("Early stopping triggered at epoch {} "
                                + "(no improvement for {} consecutive epochs, best RMSE={})",
                                epoch, patience, String.format("%.4f", bestValRmse));
                        break;
                    }
                }
            }
        }

        log.info("BMF training complete: best validation RMSE = {}",
                bestValRmse == Double.MAX_VALUE ? "N/A (no validation set)"
                        : String.format("%.4f", bestValRmse));

        BiasedMfModel model = new BiasedMfModel(
                k, globalMean, userBias, itemBias, userFactors, itemFactors);
        return new TrainingResult(model, Collections.unmodifiableList(rmseHistory));
    }

    // helpers

    /**
     * Root mean squared error over a held-out sample set: sqrt(mean((r - pred)^2)).
     * Lower is better. Users/items in valSet but not trainSet fall back to a 0
     * bias and skipped factors via getOrDefault, i.e. they're predicted as the
     * global mean.
     */
    private static double computeRmse(
            List<TrainingSample> samples,
            double               globalMean,
            Map<Long, Double>    userBias,
            Map<Long, Double>    itemBias,
            Map<Long, double[]>  userFactors,
            Map<Long, double[]>  itemFactors,
            int                  k) {

        double sumSqErr = 0.0;
        for (TrainingSample s : samples) {
            double bu = userBias.getOrDefault(s.userId(), 0.0);
            double bi = itemBias.getOrDefault(s.eventId(), 0.0);
            double[] pu = userFactors.get(s.userId());
            double[] qi = itemFactors.get(s.eventId());

            double pred = globalMean + bu + bi;
            if (pu != null && qi != null) {
                pred += dot(pu, qi);
            }

            double err = s.rating() - pred;
            sumSqErr += err * err;
        }
        return Math.sqrt(sumSqErr / samples.size());
    }

    /** Dot product of two equal-length vectors. */
    private static double dot(double[] a, double[] b) {
        double sum = 0.0;
        for (int i = 0; i < a.length; i++) {
            sum += a[i] * b[i];
        }
        return sum;
    }

    /**
     * Factor vector seeded from N(0, 0.1). The small random values break symmetry
     * (all-zero init would keep every factor identical and updates symmetric) while
     * staying close enough to the mean to avoid large early gradients.
     */
    private static double[] randomVector(int k, Random rng) {
        double[] v = new double[k];
        for (int f = 0; f < k; f++) {
            v[f] = rng.nextGaussian() * 0.1;
        }
        return v;
    }

    /** Empty, zeroed-out model, returned when there's no data to train on. */
    private static BiasedMfModel emptyModel(int k) {
        return new BiasedMfModel(k, 0.0,
                new HashMap<>(), new HashMap<>(),
                new HashMap<>(), new HashMap<>());
    }
}
