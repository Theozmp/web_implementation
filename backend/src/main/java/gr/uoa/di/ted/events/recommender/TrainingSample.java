package gr.uoa.di.ted.events.recommender;

/**
 * One observed (user, item, rating) triple for SGD training.
 *
 * Implicit-feedback ratings: 5.0 if the user has a confirmed booking for the event
 * (our strongest positive signal), 1.0 if they only viewed the detail page. When
 * both exist the booking wins (5.0). We store one sample per (user, event) pair;
 * duplicates would skew the global mean and bias estimates.
 */
public record TrainingSample(long userId, long eventId, double rating) {}
