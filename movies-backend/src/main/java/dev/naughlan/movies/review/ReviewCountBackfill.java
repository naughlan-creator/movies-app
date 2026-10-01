package dev.naughlan.movies.review;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.group;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.match;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.newAggregation;

import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

/**
 * Gives movies that have reviews but no reviewCount yet (reviews written before the event pipeline
 * existed) their count. After that, ReviewCountProjector keeps it current. Runs after the review
 * link migration so every review already knows its movie.
 */
@Component
@Order(2)
class ReviewCountBackfill implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ReviewCountBackfill.class);

    private final MongoTemplate mongoTemplate;

    ReviewCountBackfill(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        backfill();
    }

    long backfill() {
        // One pass over reviews: { _id: imdbId, count: n } per movie
        var counts = mongoTemplate.aggregate(
                newAggregation(match(Criteria.where("imdbId").ne(null)), group("imdbId").count().as("count")),
                "reviews", Document.class);

        long updated = 0;
        for (Document row : counts) {
            updated += mongoTemplate.updateFirst(
                    Query.query(Criteria.where("imdbId").is(row.getString("_id")).and("reviewCount").exists(false)),
                    new Update().set("reviewCount", ((Number) row.get("count")).longValue()),
                    "movies").getModifiedCount();
        }
        if (updated > 0) {
            log.info("Backfilled reviewCount on {} movies", updated);
        }
        return updated;
    }
}
