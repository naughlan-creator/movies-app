package dev.naughlan.movies.ai;

import java.time.Instant;

/**
 * @param refreshing true when new reviews arrived after this digest was written; a fresh one is on its way
 */
public record DigestResponse(ReviewDigest digest, int reviewsUsed, Instant generatedAt, boolean refreshing) {

    static DigestResponse from(StoredDigest stored) {
        return new DigestResponse(stored.digest(), stored.reviewsUsed(), stored.generatedAt(),
                stored.staleSince() != null);
    }
}