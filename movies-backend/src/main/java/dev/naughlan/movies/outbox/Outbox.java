package dev.naughlan.movies.outbox;

import java.time.Instant;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.json.JsonMapper;

/**
 * Records an event to publish later. Callers must already be in a transaction (MANDATORY):
 * an outbox write outside the business change's transaction would defeat the purpose.
 */
@Component
public class Outbox {

    private final MongoTemplate mongoTemplate;
    private final JsonMapper jsonMapper;

    public Outbox(MongoTemplate mongoTemplate, JsonMapper jsonMapper) {
        this.mongoTemplate = mongoTemplate;
        this.jsonMapper = jsonMapper;
    }

    /**
     * @param key Kafka message key. Events with the same key go to the same partition, so their
     *            order is preserved (e.g. all events for one movie).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(String topic, String key, Object event) {
        String payload = jsonMapper.writeValueAsString(event);
        mongoTemplate.insert(new OutboxEvent(null, topic, key, event.getClass().getSimpleName(),
                payload, Instant.now(), null, 0));
    }
}
