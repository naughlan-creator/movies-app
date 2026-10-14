package dev.naughlan.movies.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.qdrant.QdrantContainer;

import dev.naughlan.movies.TestcontainersConfiguration;
import dev.naughlan.movies.movie.Movie;
import dev.naughlan.movies.movie.MovieNotFoundException;
import dev.naughlan.movies.movie.MovieRepository;

@SpringBootTest(properties = {
        "app.search.enabled=true",
        "app.search.dimensions=3",
        "app.search.reconcile-initial-delay=1h",
        "app.search.reconcile-interval=1h"
})
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class SearchIntegrationTest {

    static final QdrantContainer QDRANT = new QdrantContainer("qdrant/qdrant:v1.19.2");

    static {
        QDRANT.start();
    }

    @DynamicPropertySource
    static void qdrantUrl(DynamicPropertyRegistry registry) {
        registry.add("app.search.qdrant-url", () -> "http://" + QDRANT.getHost() + ":" + QDRANT.getMappedPort(6333));
    }

    @MockitoBean
    private EmbeddingClient embeddings;

    @Autowired
    private SearchService searchService;

    @Autowired
    private MovieIndexer indexer;

    @Autowired
    private QdrantClient qdrant;

    @Autowired
    private MovieRepository movieRepository;

    @Autowired
    private SearchProperties properties;

    @BeforeEach
    void setUp() {
        movieRepository.deleteAll();
        qdrant.deleteCollection(properties.collection());
        movieRepository.saveAll(List.of(
                movie("tt0000001", "Harbour Lights"),
                movie("tt0000002", "Paper Moons"),
                movie("tt0000003", "Turbo Heist 4")));
        // Harbour Lights and Paper Moons point in nearly the same direction; Turbo Heist 4 is far from both
        when(embeddings.embedDocuments(anyList())).thenAnswer(call -> {
            List<String> texts = call.getArgument(0);
            return texts.stream().map(text -> text.startsWith("Harbour") ? new float[] {1f, 0.1f, 0f}
                    : text.startsWith("Paper") ? new float[] {0.9f, 0.3f, 0f}
                    : new float[] {0f, 0f, 1f}).toList();
        });
        indexer.reconcile();
    }

    @Test
    void theClosestMovieComesFirstAndMoviesComeFromMongo() {
        when(embeddings.embedQuery("loud car chases")).thenReturn(new float[] {0f, 0.1f, 1f});

        List<SearchService.Hit> hits = searchService.search("loud car chases", 3);

        assertThat(hits).hasSize(3);
        assertThat(hits.getFirst().movie().title()).isEqualTo("Turbo Heist 4");
        // Best match first: every score is at least as high as the next one
        assertThat(hits).extracting(SearchService.Hit::score).isSortedAccordingTo(java.util.Comparator.reverseOrder());
    }

    @Test
    void similarMoviesNeverIncludeTheMovieItself() {
        List<SearchService.Hit> hits = searchService.similar("tt0000001", 5);

        assertThat(hits).extracting(hit -> hit.movie().imdbId())
                .doesNotContain("tt0000001")
                .first().isEqualTo("tt0000002");
    }

    @Test
    void aMovieThatIsNotIndexedYetHasNoSimilarMoviesInsteadOfAnError() {
        movieRepository.save(movie("tt0000004", "Brand New Release"));

        assertThat(searchService.similar("tt0000004", 5)).isEmpty();
    }

    @Test
    void anUnknownMovieIs404() {
        assertThatThrownBy(() -> searchService.similar("tt9999999", 5)).isInstanceOf(MovieNotFoundException.class);
    }

    private static Movie movie(String imdbId, String title) {
        Movie movie = new Movie();
        movie.setImdbId(imdbId);
        movie.setTitle(title);
        return movie;
    }
}
