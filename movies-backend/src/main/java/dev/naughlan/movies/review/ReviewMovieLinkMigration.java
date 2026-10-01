package dev.naughlan.movies.review;

import java.util.List;

import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

/**
 * One-off data migration, run at every startup until there is nothing left to do.
 *
 * Old layout:  movies.reviewIds = [reviewId, ...]   (the movie lists its reviews)
 * New layout:  reviews.imdbId   = "tt..."           (each review points at its movie)
 *
 * It works on raw documents (not the Movie class) because the old field no longer exists in the code.
 * It's idempotent: it only touches movies that still have reviewIds, and removes that field last,
 * so a crash part-way leaves a state the next run finishes. Real projects often use a tool such as
 * Mongock (or Flyway for SQL) to record which migrations have run.
 */
@Component
class ReviewMovieLinkMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ReviewMovieLinkMigration.class);

    private final MongoTemplate mongoTemplate;

    ReviewMovieLinkMigration(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        migrate();
    }

    long migrate() {
        Query moviesWithReviewList = Query.query(Criteria.where("reviewIds").exists(true));
        moviesWithReviewList.fields().include("imdbId").include("reviewIds");
        List<Document> movies = mongoTemplate.find(moviesWithReviewList, Document.class, "movies");

        long linked = 0;
        for (Document movie : movies) {
            List<Object> reviewIds = movie.getList("reviewIds", Object.class);
            if (reviewIds != null && !reviewIds.isEmpty()) {
                linked += mongoTemplate.updateMulti(
                        Query.query(Criteria.where("_id").in(reviewIds).and("imdbId").exists(false)),
                        new Update().set("imdbId", movie.getString("imdbId")),
                        "reviews").getModifiedCount();
            }
            // Only after its reviews point at it does the movie lose the old list
            mongoTemplate.updateFirst(
                    Query.query(Criteria.where("_id").is(movie.get("_id"))),
                    new Update().unset("reviewIds"),
                    "movies");
        }
        if (!movies.isEmpty()) {
            log.info("Review migration: linked {} reviews to their movies and removed reviewIds from {} movies",
                    linked, movies.size());
        }
        return linked;
    }
}
