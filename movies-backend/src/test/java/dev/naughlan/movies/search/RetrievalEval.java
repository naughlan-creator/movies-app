package dev.naughlan.movies.search;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.web.client.RestClient;
import org.testcontainers.qdrant.QdrantContainer;

import dev.naughlan.movies.movie.Movie;
import tools.jackson.databind.json.JsonMapper;

/**
 * Measures search quality on a fixed catalogue (src/test/resources/eval/retrieval-cases.json), using the real
 * document text (MovieDocument), the real embedding model (the GPU "embeddings" service) and a real Qdrant.
 * Free to run, but it needs the embedding server, so it's opt-in:
 *   .\mvnw test "-Dtest=RetrievalEval" "-Deval=true"                              production settings
 *   .\mvnw test "-Dtest=RetrievalEval" "-Deval=true" "-Deval.variant=no-instruction" "-Deval.instruction=none"
 * Writes evals/search-retrieval/<variant>/results.jsonl.
 */
@Tag("eval")
@EnabledIfSystemProperty(named = "eval", matches = "true")
class RetrievalEval {

    private static final int K = 5;
    private static final String COLLECTION = "retrieval_eval";

    record Catalogue(List<CatalogueMovie> movies, List<Query> queries) {
    }

    record CatalogueMovie(String id, String title, String year, List<String> genres, String overview) {
    }

    record Query(String id, List<String> tags, String text, List<String> relevant) {
    }

    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    void run() throws IOException {
        Catalogue catalogue;
        try (InputStream in = getClass().getResourceAsStream("/eval/retrieval-cases.json")) {
            catalogue = json.readValue(in, Catalogue.class);
        }
        // The production instruction, read from application.properties, unless a variant overrides it
        Properties app = new Properties();
        try (InputStream in = getClass().getResourceAsStream("/application.properties")) {
            app.load(in);
        }
        String instruction = System.getProperty("eval.instruction", app.getProperty("app.search.query-instruction"));
        boolean useInstruction = !"none".equals(instruction);
        String variant = System.getProperty("eval.variant", "baseline");

        SearchProperties properties = new SearchProperties(true,
                System.getProperty("eval.embeddings-url", "http://localhost:18086"), null, null, COLLECTION, 1024, 32,
                useInstruction ? instruction : "");
        TeiEmbeddingClient embeddings = new TeiEmbeddingClient(RestClient.builder(), properties);

        try (QdrantContainer qdrantContainer = new QdrantContainer("qdrant/qdrant:v1.19.2")) {
            qdrantContainer.start();
            QdrantClient qdrant = new QdrantClient(RestClient.builder(), new SearchProperties(true, null,
                    "http://" + qdrantContainer.getHost() + ":" + qdrantContainer.getMappedPort(6333),
                    null, COLLECTION, 1024, 32, ""));

            // Index the catalogue exactly as MovieIndexer would: same document text, batched embedding
            qdrant.createCollection(COLLECTION, properties.dimensions());
            List<String> texts = catalogue.movies().stream().map(RetrievalEval::documentText).toList();
            List<float[]> vectors = embeddings.embedDocuments(texts);
            List<QdrantClient.Point> points = new ArrayList<>();
            for (int i = 0; i < catalogue.movies().size(); i++) {
                CatalogueMovie m = catalogue.movies().get(i);
                points.add(new QdrantClient.Point(MovieIndexer.pointId(m.id()), vectors.get(i),
                        Map.of("imdbId", m.id(), "title", m.title())));
            }
            qdrant.upsert(COLLECTION, points);

            Map<String, String> titles = catalogue.movies().stream()
                    .collect(Collectors.toMap(CatalogueMovie::id, CatalogueMovie::title));
            Path dir = Path.of(System.getProperty("eval.out", "../evals/search-retrieval")).resolve(variant);
            Files.createDirectories(dir);
            Path results = dir.resolve("results.jsonl");
            Files.deleteIfExists(results); // free and fast: always a complete fresh run, no resume needed

            List<double[]> scores = new ArrayList<>();
            Map<String, List<Double>> recallByTag = new TreeMap<>();
            List<Long> latencies = new ArrayList<>();
            for (Query query : catalogue.queries()) {
                long start = System.nanoTime();
                float[] q = useInstruction ? embeddings.embedQuery(query.text())
                        : embeddings.embedDocuments(List.of(query.text())).getFirst();
                List<String> top = qdrant.query(COLLECTION, q, K, null).stream()
                        .map(p -> (String) p.payload().get("imdbId")).toList();
                latencies.add((System.nanoTime() - start) / 1_000_000);

                Set<String> relevant = Set.copyOf(query.relevant());
                long found = top.stream().filter(relevant::contains).count();
                double recall = (double) found / Math.min(K, relevant.size());
                int firstRank = 0;
                for (int rank = 0; rank < top.size(); rank++) {
                    if (relevant.contains(top.get(rank))) {
                        firstRank = rank + 1;
                        break;
                    }
                }
                double reciprocalRank = firstRank == 0 ? 0 : 1.0 / firstRank;
                scores.add(new double[] {recall, reciprocalRank});
                recallByTag.computeIfAbsent(query.tags().getFirst(), t -> new ArrayList<>()).add(recall);

                System.out.printf("%s %-11s R@5=%.2f RR=%.2f  %-45s -> %s%n", query.id(), query.tags().getFirst(),
                        recall, reciprocalRank, '"' + query.text() + '"',
                        top.stream().map(id -> (relevant.contains(id) ? "+" : " ") + titles.get(id)).toList());

                Map<String, Object> row = new LinkedHashMap<>();
                row.put("prompt_id", query.id());
                row.put("rep", 0);
                row.put("prompt", query.text());
                row.put("tags", query.tags());
                row.put("status", "ok");
                row.put("grade", Map.of("recall_at_5", recall, "reciprocal_rank", reciprocalRank));
                row.put("meta", Map.of("top5", top, "relevant", query.relevant()));
                Files.writeString(results, json.writeValueAsString(row) + System.lineSeparator(),
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            }

            double meanRecall = scores.stream().mapToDouble(s -> s[0]).average().orElse(0);
            double mrr = scores.stream().mapToDouble(s -> s[1]).average().orElse(0);
            long[] sorted = latencies.stream().mapToLong(Long::longValue).sorted().toArray();
            System.out.printf("%n=== %s (instruction: %s) ===%nRecall@5 %.3f   MRR %.3f   over %d queries%n",
                    variant, useInstruction ? "on" : "off", meanRecall, mrr, scores.size());
            recallByTag.forEach((tag, values) -> System.out.printf("  %-11s Recall@5 %.2f (%d)%n", tag,
                    values.stream().mapToDouble(Double::doubleValue).average().orElse(0), values.size()));
            System.out.printf("Query latency (embed + search): p50 %d ms, p95 %d ms%n",
                    sorted[sorted.length / 2], sorted[(int) Math.ceil(sorted.length * 0.95) - 1]);

            assertThat(scores).hasSize(catalogue.queries().size());
        }
    }

    // The fixture becomes a Movie, so the real MovieDocument builds its text
    private static String documentText(CatalogueMovie m) {
        Movie movie = new Movie();
        movie.setTitle(m.title());
        movie.setReleaseDate(m.year() + "-01-01");
        movie.setGenres(m.genres());
        movie.setOverview(m.overview());
        return MovieDocument.text(movie, null);
    }
}
