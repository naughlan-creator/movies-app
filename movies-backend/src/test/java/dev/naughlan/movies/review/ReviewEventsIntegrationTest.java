package dev.naughlan.movies.review;

import static dev.naughlan.movies.TestUsers.movieFan42;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;

import dev.naughlan.movies.TestcontainersConfiguration;
import dev.naughlan.movies.movie.Movie;
import dev.naughlan.movies.movie.MovieRepository;
import dev.naughlan.movies.outbox.OutboxEvent;

/**
 * The whole pipeline against real MongoDB and Kafka containers:
 * HTTP -> review + outbox event in one transaction -> relay -> Kafka -> consumer -> movie.reviewCount.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class ReviewEventsIntegrationTest {

    private static final String IMDB_ID = "tt0000099";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MovieRepository movieRepository;

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private ConsumerFactory<?, ?> consumerFactory;

    @BeforeEach
    void setUp() {
        reviewRepository.deleteAll();
        movieRepository.deleteAll();
        Movie movie = new Movie();
        movie.setImdbId(IMDB_ID);
        movie.setTitle("Event Test Movie");
        movieRepository.save(movie);
    }

    private Long reviewCount() {
        return movieRepository.findMovieByImdbId(IMDB_ID).map(Movie::getReviewCount).orElse(null);
    }

    @Test
    void creatingAndDeletingReviewsUpdatesTheCountThroughKafka() throws Exception {
        String response = mockMvc.perform(post("/api/v1/reviews").with(movieFan42())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reviewBody": "Through the pipeline", "imdbId": "%s"}
                                """.formatted(IMDB_ID)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String reviewId = JsonPath.read(response, "$.id");

        // Eventually consistent: the count changes a moment after the HTTP response, not within it
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(reviewCount()).isEqualTo(1L));
        assertThat(mongoTemplate.findAll(OutboxEvent.class))
                .anySatisfy(event -> assertThat(event.publishedAt()).isNotNull());

        mockMvc.perform(delete("/api/v1/reviews/{id}", reviewId).with(movieFan42()))
                .andExpect(status().isNoContent());

        await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> assertThat(reviewCount()).isZero());
    }

    @Test
    void aMessageTheConsumerCannotReadEndsUpOnTheDeadLetterTopic() throws Exception {
        String poison = "this is not a review event " + System.nanoTime();
        kafkaTemplate.send(ReviewEvents.TOPIC, IMDB_ID, poison).get();

        try (Consumer<?, ?> consumer = consumerFactory.createConsumer("dlt-check-" + System.nanoTime(), "dlt-check")) {
            consumer.subscribe(List.of(ReviewEvents.DEAD_LETTER_TOPIC));
            List<Object> seen = new ArrayList<>();
            await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
                for (ConsumerRecord<?, ?> record : consumer.poll(Duration.ofMillis(500))) {
                    seen.add(record.value());
                }
                assertThat(seen).contains(poison);
            });
        }
    }
}
