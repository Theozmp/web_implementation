package gr.uoa.di.ted.events.recommender;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Hyperparameters and runtime settings for the Biased MF recommender.
 *
 * Bound from the "recommender.*" namespace in application.yml so they can be tuned
 * without recompiling. @Data supplies the getters/setters the binder needs.
 */
@Data
@Component
@ConfigurationProperties(prefix = "recommender")
public class BiasedMfConfig {

    /**
     * Number of latent dimensions per user/item factor vector. Larger captures
     * subtler patterns but trains slower and overfits more on small datasets.
     */
    private int latentFactors = 20;

    /**
     * SGD learning rate (step size along the negative gradient). Too large
     * oscillates or diverges; too small converges slowly.
     */
    private double learningRate = 0.005;

    /**
     * L2 regularization coefficient. Penalizes large parameters to curb
     * overfitting; each update decays the param toward 0 by lr * lambda * param.
     */
    private double regularization = 0.02;

    /** Maximum number of SGD passes over the training set. */
    private int epochs = 30;

    /**
     * Stop training after this many consecutive epochs with no validation-RMSE
     * improvement, to avoid wasted computation once the model has converged.
     */
    private int earlyStoppingPatience = 3;

    /**
     * Where the trained model is persisted as JSON. The parent directory is
     * created on the first save.
     */
    private String modelPath = "./data/bmf-model.json";

    /**
     * Seed-dataset settings (bound from {@code recommender.dataset.*}).
     * {@link DatasetLoader} reads this CSV once when no saved model exists, to
     * bootstrap the latent space before live bookings/views accumulate.
     */
    private final Dataset dataset = new Dataset();

    /**
     * CSV seed dataset that bootstraps the recommender.
     *
     * Ships with the Kaggle "Event Recommendation Engine Challenge"
     * {@code event_interest.csv} (user, event, invited, timestamp, interested,
     * not_interested). {@link DatasetLoader} maps each row to a rating like live
     * feedback does: interested=1 becomes 5.0 (a confirmed booking), any other
     * shown row becomes 1.0 (a page view).
     */
    @Data
    public static class Dataset {

        /**
         * Master switch for CSV bootstrap. When false, {@link DatasetLoader}
         * returns no samples and the model trains on live DB data alone.
         */
        private boolean enabled = true;

        /**
         * Spring resource location of the seed CSV. Supports {@code classpath:}
         * (the default, packaged in the jar), {@code file:/abs/path}, or a plain
         * relative path. Override with the {@code RECOMMENDER_DATASET_PATH} env
         * var to swap datasets without rebuilding.
         */
        private String path = "classpath:dataset/event_interest.csv";
    }
}
