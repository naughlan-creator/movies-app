package dev.naughlan.movies.search;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.CountDownLatch;

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

@SpringBootTest(properties = {
        "app.search.enabled=true",
        "app.search.dimensions=4",
        "app.search.reconcile-initial-delay=1h",
        "app.search.reconcile-interval=1h"
})
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class SchedulerLockIntegrationTest {

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
    private MovieRepository movieRepository;

    @Test
    void twoReplicasStartingTheSameJobAtOnceDoTheWorkOnlyOnce() throws Exception {
        movieRepository.deleteAll();
        Movie movie = new Movie();
        movie.setImdbId("tt0000001");
        movie.setTitle("Harbour Lights");
        movieRepository.save(movie);

        // The fake model holds the first run inside the embedding call until we let it go
        CountDownLatch insideTheJob = new CountDownLatch(1);
        CountDownLatch letItFinish = new CountDownLatch(1);
        when(embeddings.embedDocuments(anyList())).thenAnswer(call -> {
            insideTheJob.countDown();
            letItFinish.await(10, SECONDS);
            return List.of(new float[] {1f, 0f, 0f, 0f});
        });

        // "Replica 1" starts the job and is now busy embedding
        Thread replica1 = Thread.ofVirtual().start(indexer::scheduledReconcile);
        assertThat(insideTheJob.await(10, SECONDS)).isTrue();

        // "Replica 2" runs the same job while replica 1 still holds the lock: it must skip, not wait or repeat
        indexer.scheduledReconcile();

        letItFinish.countDown();
        replica1.join(10_000);
        verify(embeddings, times(1)).embedDocuments(anyList());
    }
}