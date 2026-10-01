package dev.naughlan.movies.outbox;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * An event waiting to be published to Kafka. It's written in the same transaction as the change
 * it describes, so the change and its event either both exist or neither does.
 *
 * @param publishedAt null until the relay has delivered it to Kafka
 * @param attempts    failed publish attempts so far (for monitoring)
 */
@Document(collection = "outbox")
public record OutboxEvent(
        @Id String id,
        String topic,
        String key,
        String type,
        String payload,
        Instant createdAt,
        Instant publishedAt,
        int attempts) {
}
