package dev.naughlan.movies.user;

import jakarta.annotation.PostConstruct;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.stereotype.Component;

@Component
class UserIndexes {

    private final MongoTemplate mongoTemplate;

    UserIndexes(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    // Runs while the app starts, before the web server accepts requests.
    // Creating an index that already exists is a no-op, so this is safe on every startup.
    @PostConstruct
    void createIndexes() {
        mongoTemplate.indexOps(User.class)
                .createIndex(new Index().on("username", Sort.Direction.ASC).unique());
    }
}