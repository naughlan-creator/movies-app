package dev.naughlan.movies.review;

import java.time.Instant;
import java.util.UUID;

/**
 * Published to Kafka (topic {@link ReviewEvents#TOPIC}, key = imdbId) whenever a review is created or deleted.
 * It carries the review text ("event-carried state"), so future consumers (e.g. moderation, sentiment,
 * embeddings) can do their work without calling back into this API.
 *
 * @param eventId unique per event, so consumers can recognise a redelivered duplicate
 */
public record ReviewEvent(
        String eventId,
        Type type,
        String reviewId,
        String imdbId,
        String authorId,
        String body,
        Instant occurredAt) {

    public enum Type { CREATED, DELETED }

    static ReviewEvent created(Review review) {
        return new ReviewEvent(UUID.randomUUID().toString(), Type.CREATED, review.getId().toHexString(),
                review.getImdbId(), review.getAuthorId(), review.getBody(), Instant.now());
    }

    static ReviewEvent deleted(Review review) {
        return new ReviewEvent(UUID.randomUUID().toString(), Type.DELETED, review.getId().toHexString(),
                review.getImdbId(), review.getAuthorId(), null, Instant.now());
    }
}
