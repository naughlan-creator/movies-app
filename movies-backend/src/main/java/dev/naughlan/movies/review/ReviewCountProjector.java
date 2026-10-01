package dev.naughlan.movies.review;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import dev.naughlan.movies.movie.Movie;
import dev.naughlan.movies.movie.MovieCache;
import tools.jackson.databind.json.JsonMapper;

/**
 * Keeps movie.reviewCount up to date from review events.
 *
 * Idempotent by design: instead of "+1 / -1" (which would double-count a redelivered event),
 * it recounts from the reviews collection, the source of truth. Duplicates, replays and
 * out-of-order delivery all end in the right number.
 */
@Component
class ReviewCountProjector {

    private static final Logger log = LoggerFactory.getLogger(ReviewCountProjector.class);

    private final JsonMapper jsonMapper;
    private final ReviewRepository reviewRepository;
    private final MongoTemplate mongoTemplate;
    private final MovieCache movieCache;

    ReviewCountProjector(JsonMapper jsonMapper, ReviewRepository reviewRepository, MongoTemplate mongoTemplate,
                         MovieCache movieCache) {
        this.jsonMapper = jsonMapper;
        this.reviewRepository = reviewRepository;
        this.mongoTemplate = mongoTemplate;
        this.movieCache = movieCache;
    }

    // Each consumer group gets its own copy of every event. Future consumers (moderation, sentiment,
    // embeddings) will use their own group ids and process the same events independently.
    @KafkaListener(topics = ReviewEvents.TOPIC, groupId = "review-count-projector")
    void on(String payload) {
        // An unparseable message throws here and goes straight to the dead-letter topic
        ReviewEvent event = jsonMapper.readValue(payload, ReviewEvent.class);
        if (event.imdbId() == null) {
            return; // legacy review that never belonged to a movie
        }
        long count = reviewRepository.countByImdbId(event.imdbId());
        mongoTemplate.updateFirst(
                Query.query(Criteria.where("imdbId").is(event.imdbId())),
                new Update().set("reviewCount", count),
                Movie.class);
        // reviewCount is part of the cached movie responses
        movieCache.invalidateAll();
        log.debug("{} {} -> movie {} now has {} reviews", event.type(), event.reviewId(), event.imdbId(), count);
    }
}
