package gr.uoa.di.ted.events.recommender;

import java.util.Map;

/**
 * Trained parameters of the Biased Matrix Factorization model.
 *
 * Prediction: pred(u,i) = globalMean + userBias[u] + itemBias[i] + dot(p_u, q_i).
 *   globalMean - average rating over all observed pairs; the baseline guess.
 *   userBias   - how much user u rates above/below the mean.
 *   itemBias   - how much event i is rated above/below the mean.
 *   p_u, q_i   - length-k latent vectors for user taste and item characteristics;
 *                their dot product is the user/item affinity. The k dimensions
 *                are unlabelled and emerge from the data.
 *
 * No Spring annotations on purpose, so it can be used outside the Spring context
 * (unit tests, ModelStore serialisation). Jackson needs the no-arg constructor
 * and getters/setters.
 */
public class BiasedMfModel {

    /** number of latent dimensions; must match the training config. */
    private int k;

    /** global mean of all training ratings. */
    private double globalMean;

    /** userId -> user bias scalar. */
    private Map<Long, Double> userBias;

    /** eventId -> item bias scalar. */
    private Map<Long, Double> itemBias;

    /** userId -> length-k user latent factor vector. */
    private Map<Long, double[]> userFactors;

    /** eventId -> length-k item latent factor vector. */
    private Map<Long, double[]> itemFactors;

    // no-arg constructor for Jackson deserialisation.
    public BiasedMfModel() {}

    public BiasedMfModel(int k, double globalMean,
                         Map<Long, Double>   userBias,    Map<Long, Double>   itemBias,
                         Map<Long, double[]> userFactors, Map<Long, double[]> itemFactors) {
        this.k           = k;
        this.globalMean  = globalMean;
        this.userBias    = userBias;
        this.itemBias    = itemBias;
        this.userFactors = userFactors;
        this.itemFactors = itemFactors;
    }

    // core prediction

    /**
     * Computes globalMean + userBias + itemBias + dot(p_u, q_i).
     *
     * Unknown users/items degrade gracefully: a missing bias is 0 and a missing
     * factor vector is skipped, so an unknown user keeps only the item signal,
     * an unknown item only the user signal, and a pair unknown on both sides
     * just returns the global mean.
     */
    public double predict(Long userId, Long eventId) {
        double score = globalMean;

        score += userBias.getOrDefault(userId, 0.0);

        score += itemBias.getOrDefault(eventId, 0.0);

        // latent factor interaction: user taste vs item characteristics
        double[] pu = userFactors.get(userId);
        double[] qi = itemFactors.get(eventId);
        if (pu != null && qi != null) {
            for (int f = 0; f < k; f++) {
                score += pu[f] * qi[f];
            }
        }

        return score;
    }

    /**
     * True if the model has a learned factor vector for this user. False means the
     * user wasn't in the training set (cold start) and we fall back to popularity.
     */
    public boolean hasUser(Long userId) {
        return userFactors != null && userFactors.containsKey(userId);
    }

    // getters/setters (needed by Jackson)

    public int getK()                                   { return k; }
    public void setK(int k)                             { this.k = k; }
    public double getGlobalMean()                       { return globalMean; }
    public void setGlobalMean(double globalMean)        { this.globalMean = globalMean; }
    public Map<Long, Double> getUserBias()              { return userBias; }
    public void setUserBias(Map<Long, Double> m)        { this.userBias = m; }
    public Map<Long, Double> getItemBias()              { return itemBias; }
    public void setItemBias(Map<Long, Double> m)        { this.itemBias = m; }
    public Map<Long, double[]> getUserFactors()         { return userFactors; }
    public void setUserFactors(Map<Long, double[]> m)   { this.userFactors = m; }
    public Map<Long, double[]> getItemFactors()         { return itemFactors; }
    public void setItemFactors(Map<Long, double[]> m)   { this.itemFactors = m; }
}
