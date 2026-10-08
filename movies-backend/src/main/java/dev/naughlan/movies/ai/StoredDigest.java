package dev.naughlan.movies.ai;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * The latest digest for one movie. staleSince is set when reviews change after it was generated,
 * and is absent while the digest is up to date. A document with only imdbId + staleSince means
 * "never generated, queued".
 */
@Document("review_digests")
public record StoredDigest(
        @Id String imdbId,
        ReviewDigest digest,
        // Boxed (Integer/Long, not int/long): a queued document has no values yet, and primitives can't be null
        Integer reviewsUsed,
        String model,
        Long inputTokens,
        Long outputTokens,
        Instant generatedAt,
        Instant staleSince,
        // SHA-256 of model + system prompt + user prompt: same hash = same input = no need to call the model again
        String inputHash
) {
}