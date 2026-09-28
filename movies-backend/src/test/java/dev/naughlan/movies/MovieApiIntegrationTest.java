package dev.naughlan.movies;

import dev.naughlan.movies.movie.Movie;
import dev.naughlan.movies.movie.MovieRepository;
import dev.naughlan.movies.review.ReviewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

    @BeforeEach
    void resetDatabase() {
        reviewRepository.deleteAll();
        movieRepository.deleteAll();

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
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reviewBody": "  Great film  ", "imdbId": "tt0000001"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.body").value("Great film"));

        mockMvc.perform(get("/api/v1/movies/tt0000001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviews.length()").value(1))
                .andExpect(jsonPath("$.reviews[0].body").value("Great film"));
    }

    @Test
    void reviewForUnknownMovieIsRejectedAndNothingIsSaved() throws Exception {
        mockMvc.perform(post("/api/v1/reviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"reviewBody": "Orphan attempt", "imdbId": "tt0000000"}
                                """))
                .andExpect(status().isNotFound());

        assertThat(reviewRepository.count()).isZero();
    }
}