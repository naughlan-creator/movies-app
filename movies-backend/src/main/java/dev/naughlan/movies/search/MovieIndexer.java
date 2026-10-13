package dev.naughlan.movies.search;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import dev.naughlan.movies.ai.ReviewDigest;
import dev.naughlan.movies.ai.ReviewDigestService;
import dev.naughlan.movies.ai.StoredDigest;
import dev.naughlan.movies.movie.Movie;
import dev.naughlan.movies.movie.MovieRepository;
import io.micrometer.core.instrument.MeterRegistry;

@Component
class MovieIndexer {

    private static final Logger log = LoggerFactory.getLogger(MovieIndexer.class);

    record Result(int movies, int embedded, int removed) {
    }

    private record Pending(Movie movie, String text, String hash) {
    }

    private final SearchProperties properties;
    private final EmbeddingClient embeddings;
    private final QdrantClient qdrant;
    private final MovieRepository movieRepository;
    private final ReviewDigestService digestService;
    private final MeterRegistry meters;

    MovieIndexer(SearchProperties properties, EmbeddingClient embeddings, QdrantClient qdrant,
            MovieRepository movieRepository, ReviewDigestService digestService, MeterRegistry meters) {
        this.properties = properties;
        this.embeddings = embeddings;
        this.qdrant = qdrant;
        this.movieRepository = movieRepository;
        this.digestService = digestService;
        this.meters = meters;
    }

    @Scheduled(fixedDelayString = "${app.search.reconcile-interval:5m}", initialDelayString = "${app.search.reconcile-initial-delay:30s}")
    void scheduledReconcile() {
        if (!properties.enabled()) {
            return;
        }
        try {
            reconcile();
        } catch (RuntimeException e) {
            // Nothing is lost: the next pass compares again and picks up where this one
            // failed
            log.warn("Search index reconcile failed, the next pass will retry: {}", e.toString());
        }
    }

    /**
     * Makes the collection match MongoDB: embeds new and changed movies, removes
     * deleted ones.
     */
    Result reconcile() {
        String collection = properties.collection();
        ensureCollection(collection);

        // What the index has now: imdbId -> hash of the text its vector was made from
        Map<String, String> indexed = new HashMap<>();
        for (QdrantClient.StoredPoint point : qdrant.scroll(collection, List.of("imdbId", "contentHash"))) {
            indexed.put((String) point.payload().get("imdbId"), (String) point.payload().get("contentHash"));
        }

        // What it should have: every movie's current text and hash
        List<Movie> movies = movieRepository.findAll();
        Set<String> current = new HashSet<>();
        List<Pending> changed = new ArrayList<>();
        for (Movie movie : movies) {
            if (movie.getImdbId() == null) {
                continue;
            }
            current.add(movie.getImdbId());
            String text = MovieDocument.text(movie, digestFor(movie.getImdbId()));
            // The collection name is part of the hash: a new collection (new model or
            // version) re-embeds everything
            String hash = sha256(collection + "\n" + text);
            if (!hash.equals(indexed.get(movie.getImdbId()))) {
                changed.add(new Pending(movie, text, hash));
            }
        }

        // Embed and store in batches: one GPU pass and one upsert per batch, never one
        // per movie
        for (int start = 0; start < changed.size(); start += properties.batchSize()) {
            List<Pending> batch = changed.subList(start, Math.min(start + properties.batchSize(), changed.size()));
            List<float[]> vectors = embeddings.embedDocuments(batch.stream().map(Pending::text).toList());
            List<QdrantClient.Point> points = new ArrayList<>(batch.size());
            for (int i = 0; i < batch.size(); i++) {
                Movie movie = batch.get(i).movie();
                points.add(new QdrantClient.Point(pointId(movie.getImdbId()), vectors.get(i), Map.of(
                        "imdbId", movie.getImdbId(),
                        "title", movie.getTitle() == null ? "" : movie.getTitle(),
                        "genres", movie.getGenres() == null ? List.of() : movie.getGenres(),
                        "contentHash", batch.get(i).hash())));
            }
            qdrant.upsert(collection, points);
        }

        List<String> removed = indexed.keySet().stream().filter(imdbId -> !current.contains(imdbId)).toList();
        if (!removed.isEmpty()) {
            qdrant.delete(collection, removed.stream().map(MovieIndexer::pointId).toList());
        }

        meters.counter("app.search.indexed", "outcome", "embedded").increment(changed.size());
        meters.counter("app.search.indexed", "outcome", "removed").increment(removed.size());
        if (!changed.isEmpty() || !removed.isEmpty()) {
            log.info("Search index {}: {} movies, {} embedded, {} removed", collection, movies.size(),
                    changed.size(), removed.size());
        }
        return new Result(movies.size(), changed.size(), removed.size());
    }

    private void ensureCollection(String collection) {
        if (!qdrant.collectionExists(collection)) {
            qdrant.createCollection(collection, properties.dimensions());
            qdrant.createKeywordIndex(collection, "genres");
            log.info("Created search collection {}", collection);
        }
        // The very first index: point the alias at it. Moving the alias to a NEW
        // version is a separate step (Part C).
        if (qdrant.aliasTarget(properties.alias()) == null) {
            qdrant.switchAlias(properties.alias(), collection, false);
        }
    }

    private ReviewDigest digestFor(String imdbId) {
        return digestService.find(imdbId).map(StoredDigest::digest).orElse(null);
    }

    // Qdrant ids must be numbers or UUIDs. The same imdbId always gives the same
    // UUID,
    // so re-indexing a movie replaces its point instead of adding a duplicate.
    static String pointId(String imdbId) {
        return UUID.nameUUIDFromBytes(imdbId.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static String sha256(String text) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Every JVM must support SHA-256", e);
        }
    }
}