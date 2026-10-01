package dev.naughlan.movies.review;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Kafka topics for review events. Spring's KafkaAdmin creates them at startup if they don't exist.
 */
@Configuration
public class ReviewEvents {

    public static final String TOPIC = "movies.reviews";
    // Where messages go after a consumer has failed on them (see KafkaErrorHandlingConfig).
    // "-dlt" is Spring Kafka 4's default suffix (older versions used ".DLT").
    public static final String DEAD_LETTER_TOPIC = TOPIC + "-dlt";

    // Partitions are the unit of parallelism: up to 3 consumers in one group can share the work.
    // Messages with the same key (imdbId) always land in the same partition, so per-movie order is kept.
    @Bean
    NewTopic reviewEventsTopic() {
        return TopicBuilder.name(TOPIC).partitions(3).replicas(1).build();
    }

    // The default recoverer sends a failed message to the same partition number of the DLT, so it needs as many
    @Bean
    NewTopic reviewEventsDeadLetterTopic() {
        return TopicBuilder.name(DEAD_LETTER_TOPIC).partitions(3).replicas(1).build();
    }
}
