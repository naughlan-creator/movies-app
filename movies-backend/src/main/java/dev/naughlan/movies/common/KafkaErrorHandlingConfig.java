package dev.naughlan.movies.common;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

import tools.jackson.core.JacksonException;

/**
 * What happens when a Kafka listener throws: retry a couple of times (the failure may be temporary,
 * e.g. the database blipped), then move the message to "<topic>-dlt" (the dead-letter topic) so one
 * bad message can't block its partition forever. Messages that can't be parsed are never retried:
 * retrying won't fix them.
 */
@Configuration
public class KafkaErrorHandlingConfig {

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> kafkaTemplate) {
        DefaultErrorHandler handler = new DefaultErrorHandler(
                new DeadLetterPublishingRecoverer(kafkaTemplate),
                new FixedBackOff(1000L, 2));
        handler.addNotRetryableExceptions(JacksonException.class);
        return handler;
    }
}
