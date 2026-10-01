package dev.naughlan.movies.review;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.context.ActiveProfiles;

import dev.naughlan.movies.TestcontainersConfiguration;

// Same annotations as the other integration tests, so Spring reuses one context and one MongoDB container
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class ReviewMovieLinkMigrationIntegrationTest {

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private ReviewMovieLinkMigration migration;

    @BeforeEach
    void clean() {
        mongoTemplate.dropCollection("movies");
        mongoTemplate.dropCollection("reviews");
    }

    @Test
    void linksReviewsToTheirMovieAndRemovesTheOldList() {
        // Data in the OLD layout: the movie lists its reviews; reviews don't know their movie
        ObjectId first = new ObjectId();
        ObjectId second = new ObjectId();
        mongoTemplate.insert(new Document("_id", first).append("body", "Loved it"), "reviews");
        mongoTemplate.insert(new Document("_id", second).append("body", "Too long"), "reviews");
        mongoTemplate.insert(new Document("imdbId", "tt0000001").append("title", "Old Movie")
                .append("reviewIds", List.of(first, second)), "movies");

        long linked = migration.migrate();

        assertThat(linked).isEqualTo(2);
        assertThat(mongoTemplate.findAll(Document.class, "reviews"))
                .allSatisfy(review -> assertThat(review.getString("imdbId")).isEqualTo("tt0000001"));
        assertThat(mongoTemplate.findAll(Document.class, "movies"))
                .allSatisfy(movie -> assertThat(movie.containsKey("reviewIds")).isFalse());
    }

    @Test
    void runningItAgainChangesNothing() {
        ObjectId reviewId = new ObjectId();
        mongoTemplate.insert(new Document("_id", reviewId).append("body", "Once"), "reviews");
        mongoTemplate.insert(new Document("imdbId", "tt0000002").append("reviewIds", List.of(reviewId)), "movies");

        migration.migrate();

        assertThat(migration.migrate()).isZero();
    }
}
