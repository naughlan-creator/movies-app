package dev.naughlan.movies.ai;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

/**
 * Which digests need regenerating. The "queue" is just the staleSince field in review_digests:
 * no extra infrastructure, and it survives restarts.
 */
@Component
class DigestQueue {

    private final MongoTemplate mongoTemplate;

    DigestQueue(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    /**
     * Queue a movie's digest for regeneration. One Mongo write, no AI, safe to repeat:
     * - upsert creates the document if the movie has never had a digest
     * - $min keeps the EARLIEST stale time, so a stream of reviews can't keep pushing the refresh back
     */
    void markStale(String imdbId) {
        mongoTemplate.upsert(Query.query(Criteria.where("_id").is(imdbId)),
                new Update().min("staleSince", Instant.now()), StoredDigest.class);
    }

    /** Digests that have been stale for at least {@code delay}, oldest first. */
    List<StoredDigest> due(Duration delay, int limit) {
        Query query = Query.query(Criteria.where("staleSince").lte(Instant.now().minus(delay)))
                .with(Sort.by("staleSince"))
                .limit(limit);
        return mongoTemplate.find(query, StoredDigest.class);
    }

    /** After a failure, don't retry for a while: every attempt costs money. */
    void postpone(String imdbId, Duration backoff) {
        mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is(imdbId)),
                new Update().set("staleSince", Instant.now().plus(backoff)), StoredDigest.class);
    }
}