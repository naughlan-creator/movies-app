package dev.naughlan.movies;

import static dev.naughlan.movies.TestUsers.MOVIE_FAN_43_ID;
import static dev.naughlan.movies.TestUsers.movieFan42;
import static dev.naughlan.movies.TestUsers.movieFan43;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.jayway.jsonpath.JsonPath;

import dev.naughlan.movies.movie.Movie;
import dev.naughlan.movies.movie.MovieCache;
import dev.naughlan.movies.movie.MovieRepository;
import dev.naughlan.movies.review.ReviewRepository;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class MovieApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MovieRepository movieRepository;

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private MovieCache movieCache;

    @BeforeEach
    void resetDatabase() {
        reviewRepository.deleteAll();
        movieRepository.deleteAll();
        // The data just changed behind the cache's back, so drop what earlier tests cached
        movieCache.invalidateAll();

        Movie movie = new Movie();
        movie.setImdbId("tt0000001");
        movie.setTitle("Integration Test Movie");
        movie.setTrendingRank(1);
        movieRepository.save(movie);
    }

    @Test
    void listsTrendingMoviesFromTheDatabase() throws Exception {
        mockMvc.perform(get("/api/v1/movies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].title").value("Integration Test Movie"));
    }

    @Test
    void savedReviewIsReturnedWithItsMovie() throws Exception {
        mockMvc.perform(post("/api/v1/reviews")
                        .with(movieFan43())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reviewBody": "  Great film  ", "imdbId": "tt0000001"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.body").value("Great film"))
                .andExpect(jsonPath("$.authorId").value(MOVIE_FAN_43_ID))
                .andExpect(jsonPath("$.authorName").value("movie_fan_43"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty());

        mockMvc.perform(get("/api/v1/movies/tt0000001/reviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].body").value("Great film"))
                .andExpect(jsonPath("$.items[0].authorName").value("movie_fan_43"));
    }

    @Test
    void reviewForUnknownMovieIsRejectedAndNothingIsSaved() throws Exception {
        mockMvc.perform(post("/api/v1/reviews")
                        .with(movieFan43())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reviewBody": "Orphan attempt", "imdbId": "tt0000000"}
                                """))
                .andExpect(status().isNotFound());

        assertThat(reviewRepository.count()).isZero();
    }

    @Test
    void postingAReviewWithoutLoggingInIs401() throws Exception {
        mockMvc.perform(post("/api/v1/reviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reviewBody": "Anonymous", "imdbId": "tt0000001"}
                                """))
                .andExpect(status().isUnauthorized());

        assertThat(reviewRepository.count()).isZero();
    }

    private String postReview(RequestPostProcessor author, String body) throws Exception {
        String response = mockMvc.perform(post("/api/v1/reviews")
                        .with(author)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reviewBody": "%s", "imdbId": "tt0000001"}
                                """.formatted(body)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.id");
    }

    @Test
    void authorCanDeleteTheirOwnReview() throws Exception {
        // movie_fan_42 is a regular user, so this really exercises the ownership check, not the admin bypass
        String reviewId = postReview(movieFan42(), "Changed my mind");

        mockMvc.perform(delete("/api/v1/reviews/{id}", reviewId).with(movieFan42()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/movies/tt0000001/reviews"))
                .andExpect(jsonPath("$.totalItems").value(0));
        assertThat(reviewRepository.count()).isZero();
    }

    @Test
    void anotherUserCannotDeleteSomeoneElsesReview() throws Exception {
        // movie_fan_42 (a regular user) tries to delete movie_fan_43's review: the BOLA attack
        String reviewId = postReview(movieFan43(), "My honest opinion");

        mockMvc.perform(delete("/api/v1/reviews/{id}", reviewId).with(movieFan42()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Forbidden"));

        assertThat(reviewRepository.count()).isEqualTo(1);
    }

    @Test
    void adminCanDeleteAnyReview() throws Exception {
        // movie_fan_43 is the admin (as in the Keycloak realm) and removes movie_fan_42's review
        String reviewId = postReview(movieFan42(), "Spam spam spam");

        mockMvc.perform(delete("/api/v1/reviews/{id}", reviewId).with(movieFan43()))
                .andExpect(status().isNoContent());

        assertThat(reviewRepository.count()).isZero();
    }
    @Test
    void reviewsArePagedNewestFirst() throws Exception {
        postReview(movieFan43(), "first");
        postReview(movieFan42(), "second");
        postReview(movieFan43(), "third");

        mockMvc.perform(get("/api/v1/movies/tt0000001/reviews").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.items[0].body").value("third"))
                .andExpect(jsonPath("$.items[1].body").value("second"));

        mockMvc.perform(get("/api/v1/movies/tt0000001/reviews").param("size", "2").param("page", "1"))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].body").value("first"));
    }

    @Test
    void reviewsOfAnUnknownMovieAre404() throws Exception {
        mockMvc.perform(get("/api/v1/movies/tt0000000/reviews"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Movie not found"));
    }
}
