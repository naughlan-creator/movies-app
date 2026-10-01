package dev.naughlan.movies.common;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;

/**
 * Enables @Transactional for MongoDB: several writes either all happen or none do.
 * MongoDB needs a replica set for this; Atlas, the compose "mongo" service and Testcontainers all are.
 */
@Configuration
public class MongoTransactionConfig {

    @Bean
    public MongoTransactionManager transactionManager(MongoDatabaseFactory databaseFactory) {
        return new MongoTransactionManager(databaseFactory);
    }
}
