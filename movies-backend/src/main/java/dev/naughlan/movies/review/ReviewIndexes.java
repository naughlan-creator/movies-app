package dev.naughlan.movies.review;

import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Component
class ReviewIndexes {

    private final MongoTemplate mongoTemplate;

    ReviewIndexes(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    // Matches the query exactly (filter by imdbId, sort by createdAt then _id, newest first), so MongoDB
    // reads only the page it needs instead of scanning and sorting every review.
    // Creating an index that already exists is a no-op, so this is safe on every startup.
    @PostConstruct
    void createIndexes() {
        mongoTemplate.indexOps(Review.class).createIndex(new Index()
                .on("imdbId", Sort.Direction.ASC)
                .on("createdAt", Sort.Direction.DESC)
                .on("_id", Sort.Direction.DESC)
                .named("movie_reviews_newest_first"));
    }
}
