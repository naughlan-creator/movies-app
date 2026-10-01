package dev.naughlan.movies.outbox;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

/**
 * Publishes outbox events to Kafka, oldest first, and marks them published.
 *
 * Delivery is at-least-once: if the app dies after Kafka accepted an event but before it's marked,
 * the event is sent again on restart. Consumers must therefore be idempotent.
 * With several app instances, each would run this relay; a lock (e.g. ShedLock) or claiming events
 * with findAndModify would stop them publishing the same event twice.
 */
@Component
class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private static final int BATCH_SIZE = 100;

    private final MongoTemplate mongoTemplate;
    private final KafkaTemplate<String, String> kafkaTemplate;

    OutboxRelay(MongoTemplate mongoTemplate, KafkaTemplate<String, String> kafkaTemplate) {
        this.mongoTemplate = mongoTemplate;
        this.kafkaTemplate = kafkaTemplate;
    }

    // Published events are deleted automatically after a week (a TTL index); they're only kept for debugging
    @PostConstruct
    void createIndexes() {
        mongoTemplate.indexOps(OutboxEvent.class).createIndex(new Index()
                .on("publishedAt", Sort.Direction.ASC)
                .expire(Duration.ofDays(7))
                .named("published_events_ttl"));
    }

    @Scheduled(fixedDelayString = "${app.outbox.poll-interval:1s}")
    void publishPending() {
        Query pending = Query.query(Criteria.where("publishedAt").is(null))
                .with(Sort.by("createdAt"))
                .limit(BATCH_SIZE);
        List<OutboxEvent> events = mongoTemplate.find(pending, OutboxEvent.class);

        for (OutboxEvent event : events) {
            try {
                // Wait for Kafka's acknowledgement before marking the event as published
                kafkaTemplate.send(event.topic(), event.key(), event.payload()).get(10, TimeUnit.SECONDS);
                mark(event, new Update().set("publishedAt", Instant.now()));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (ExecutionException | TimeoutException | RuntimeException e) {
                mark(event, new Update().inc("attempts", 1));
                log.warn("Could not publish outbox event {} to {}; retrying on the next poll: {}",
                        event.id(), event.topic(), e.getMessage());
                // Stop here so later events don't overtake this one; ordering matters to consumers
                return;
            }
        }
    }

    private void mark(OutboxEvent event, Update update) {
        mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is(event.id())), update, OutboxEvent.class);
    }
}
