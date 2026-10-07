package dev.naughlan.movies.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import dev.naughlan.movies.review.ReviewEvent;
import dev.naughlan.movies.review.ReviewEvents;
import tools.jackson.databind.json.JsonMapper;

@Component
class DigestInvalidator {

    private static final Logger log = LoggerFactory.getLogger(DigestInvalidator.class);

    private final JsonMapper jsonMapper;
    private final DigestQueue queue;

    DigestInvalidator(JsonMapper jsonMapper, DigestQueue queue) {
        this.jsonMapper = jsonMapper;
        this.queue = queue;
    }

    // Its own consumer group, so it receives every review event independently of the review-count projector.
    // "latest": on its very first start, don't replay the topic's whole history (each replayed movie would
    // become a paid regeneration). Trending movies without a digest are picked up by the refresher instead.
    @KafkaListener(topics = ReviewEvents.TOPIC, groupId = "review-digest",
            properties = "auto.offset.reset=${app.ai.digest.kafka-offset-reset:latest}")
    void on(String payload) {
        ReviewEvent event = jsonMapper.readValue(payload, ReviewEvent.class);
        if (event.imdbId() == null) {
            return;
        }
        queue.markStale(event.imdbId());
        log.debug("{} review {} -> digest for {} marked stale", event.type(), event.reviewId(), event.imdbId());
    }
}