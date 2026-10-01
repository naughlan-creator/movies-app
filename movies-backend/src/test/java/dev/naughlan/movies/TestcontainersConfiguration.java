package dev.naughlan.movies;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

// Public so integration tests in feature packages (e.g. review) can import it
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    // Transactions (used by the outbox) need a replica set. In Testcontainers 2.x that's opt-in
    // (1.x always started one), hence withReplicaSet().
    @Bean
    @ServiceConnection
    MongoDBContainer mongoDbContainer() {
        return new MongoDBContainer(DockerImageName.parse("mongo:8.0")).withReplicaSet();
    }

    // Redis has no dedicated Testcontainers module class here; the "redis" name tells Spring Boot what it is
    @Bean
    @ServiceConnection(name = "redis")
    GenericContainer<?> redisContainer() {
        return new GenericContainer<>(DockerImageName.parse("redis:8.8.3-alpine")).withExposedPorts(6379);
    }

    // Same image as docker-compose, in KRaft mode
    @Bean
    @ServiceConnection
    KafkaContainer kafkaContainer() {
        return new KafkaContainer(DockerImageName.parse("apache/kafka:4.3.1"));
    }
}
