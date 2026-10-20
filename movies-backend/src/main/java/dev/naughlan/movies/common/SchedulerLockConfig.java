package dev.naughlan.movies.common;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.mongo.MongoLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;

/**
 * With several backend replicas, every @Scheduled job would run in every pod. A method annotated with
 * @SchedulerLock first takes a named lock (a document in MongoDB's "shedLock" collection); a pod that finds
 * the lock taken simply skips that run. So each job runs in exactly one pod at a time.
 */
@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "PT10M")
class SchedulerLockConfig {

    // Locks are stored as { _id: lock name, lockUntil, lockedAt, lockedBy } in the "shedLock" collection
    @Bean
    LockProvider lockProvider(MongoTemplate mongoTemplate) {
        return new MongoLockProvider(mongoTemplate.getDb());
    }
}