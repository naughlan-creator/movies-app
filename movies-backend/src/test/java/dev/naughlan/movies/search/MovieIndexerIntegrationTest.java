package dev.naughlan.movies.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
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
import dev.naughlan.movies.movie.MovieRepository;

/**
 * The reconciler against a real Qdrant, with a fake embedding model (no GPU
 * needed, so it runs in CI).
 */
@SpringBootTest(properties = {
        "app.search.enabled=true",
        // The fake model below returns 4-number vectors
        "app.search.dimensions=4",
        // The tests call reconcile() themselves; the schedule stays out of the way
        "app.search.reconcile-initial-delay=1h",
        "app.search.reconcile-interval=1h"
})
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class MovieIndexerIntegrationTest {

    // Same image as docker-compose. Started once for the whole class.
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
        // A stand-in model: a deterministic 4-number vector per text
        when(embeddings.embedDocuments(anyList())).thenAnswer(call -> {
            List<String> texts = call.getArgument(0);
            return texts.stream().map(MovieIndexerIntegrationTest::fakeVector).toList();
        });
        movieRepository.saveAll(List.of(
                movie("tt0000001", "Harbour Lights", "Two sisters run a failing ferry business."),
                movie("tt0000002", "Turbo Heist 4", "One last job across the city's highways."),
                movie("tt0000003", "Glass Orchard", "A strange orchard grows on a dying planet.")));
    }

    @Test
    void theFirstPassIndexesEveryMovieAndTheSecondEmbedsNothing() {
        assertThat(indexer.reconcile()).isEqualTo(new MovieIndexer.Result(3, 3, 0));
        assertThat(qdrant.scroll(properties.collection(), List.of("imdbId"))).hasSize(3);
        // The first pass also creates the alias the app searches through
        assertThat(qdrant.aliasTarget(properties.alias())).isEqualTo(properties.collection());

        clearInvocations(embeddings);
        assertThat(indexer.reconcile()).isEqualTo(new MovieIndexer.Result(3, 0, 0));
        // Unchanged movies never reach the model again
        verify(embeddings, never()).embedDocuments(anyList());
    }

    @Test
    void aChangedMovieIsReembeddedAndADeletedOneIsRemoved() {
        indexer.reconcile();

        Movie changed = movieRepository.findMovieByImdbId("tt0000001").orElseThrow();
        changed.setOverview("Two sisters fight to save their family's ferry and each other.");
        movieRepository.save(changed);
        movieRepository.delete(movieRepository.findMovieByImdbId("tt0000002").orElseThrow());

        assertThat(indexer.reconcile()).isEqualTo(new MovieIndexer.Result(2, 1, 1));
        assertThat(qdrant.scroll(properties.collection(), List.of("imdbId")))
                .extracting(point -> point.payload().get("imdbId"))
                .containsExactlyInAnyOrder("tt0000001", "tt0000003");
    }

    @Test
    void aWipedIndexRepairsItself() {
        indexer.reconcile();
        qdrant.deleteCollection(properties.collection());

        // Reconciliation compares with what's actually there, so it notices the loss
        // and rebuilds
        assertThat(indexer.reconcile()).isEqualTo(new MovieIndexer.Result(3, 3, 0));
        assertThat(qdrant.scroll(properties.collection(), List.of("imdbId"))).hasSize(3);
    }

    @Test
    void theAliasMovesToANewVersionOnlyOnceItIsComplete() {
        // Searches currently run against an older index (say, an earlier model)
        String oldVersion = "movies_old-model_v0";
        qdrant.deleteCollection(oldVersion);
        qdrant.createCollection(oldVersion, properties.dimensions());
        qdrant.switchAlias(properties.alias(), oldVersion, qdrant.aliasTarget(properties.alias()) != null);

        // Building the new version fills it completely, then moves the alias in one
        // atomic step
        assertThat(indexer.reconcile()).isEqualTo(new MovieIndexer.Result(3, 3, 0));
        assertThat(qdrant.aliasTarget(properties.alias())).isEqualTo(properties.collection());
        // The old version is kept, so rolling back is a single alias switch
        assertThat(qdrant.collectionExists(oldVersion)).isTrue();
    }

    @Test
    void aFailedPassLeavesTheAliasWhereItWas() {
        String oldVersion = "movies_old-model_v0";
        qdrant.deleteCollection(oldVersion);
        qdrant.createCollection(oldVersion, properties.dimensions());
        qdrant.switchAlias(properties.alias(), oldVersion, qdrant.aliasTarget(properties.alias()) != null);
        when(embeddings.embedDocuments(anyList())).thenThrow(new IllegalStateException("GPU out of memory"));

        assertThatThrownBy(() -> indexer.reconcile()).isInstanceOf(IllegalStateException.class);
        // A half-built index never goes live
        assertThat(qdrant.aliasTarget(properties.alias())).isEqualTo(oldVersion);
    }

    private static Movie movie(String imdbId, String title, String overview) {
        Movie movie = new Movie();
        movie.setImdbId(imdbId);
        movie.setTitle(title);
        movie.setOverview(overview);
        movie.setGenres(List.of("Drama"));
        return movie;
    }

    private static float[] fakeVector(String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        float[] vector = new float[4];
        for (int i = 0; i < bytes.length; i++) {
            vector[i % 4] += bytes[i];
        }
        return vector;
    }
}