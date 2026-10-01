package dev.naughlan.movies.movie;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import dev.naughlan.movies.TestcontainersConfiguration;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class MovieCacheIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MovieRepository movieRepository;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private MovieCache movieCache;

    @BeforeEach
    void setUp() {
        movieRepository.deleteAll();
        movieCache.invalidateAll();
        Movie movie = new Movie();
        movie.setImdbId("tt0000777");
        movie.setTitle("Original title");
        movieRepository.save(movie);
    }

    private void renameInDatabase(String title) {
        mongoTemplate.updateFirst(Query.query(Criteria.where("imdbId").is("tt0000777")),
                new Update().set("title", title), Movie.class);
    }

    @Test
    void servesTheCachedResponseUntilInvalidated() throws Exception {
        mockMvc.perform(get("/api/v1/movies/tt0000777")).andExpect(jsonPath("$.title").value("Original title"));

        // Change the data behind the cache's back: the cached response is still served (a cache hit)
        renameInDatabase("Changed title");
        mockMvc.perform(get("/api/v1/movies/tt0000777")).andExpect(jsonPath("$.title").value("Original title"));

        // What the TMDB sync and the review-count consumer do after changing movies
        movieCache.invalidateAll();
        mockMvc.perform(get("/api/v1/movies/tt0000777")).andExpect(jsonPath("$.title").value("Changed title"));
    }
}
