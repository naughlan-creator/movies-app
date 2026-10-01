package dev.naughlan.movies.outbox;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

// Turns on @Scheduled, which drives the OutboxRelay. With virtual threads enabled,
// Spring Boot runs scheduled tasks on virtual threads too.
@Configuration
@EnableScheduling
class OutboxConfig {
}
