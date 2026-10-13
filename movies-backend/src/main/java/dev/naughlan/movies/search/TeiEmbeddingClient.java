package dev.naughlan.movies.search;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
class TeiEmbeddingClient implements EmbeddingClient {

    private final RestClient restClient;
    private final SearchProperties properties;

    // The injected builder is Spring Boot's: calls made with it show up in metrics and traces automatically
    TeiEmbeddingClient(RestClient.Builder builder, SearchProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(2));
        requestFactory.setReadTimeout(Duration.ofSeconds(30));
        this.restClient = builder.clone()
                .baseUrl(properties.embeddingsUrl())
                .requestFactory(requestFactory)
                .build();
        this.properties = properties;
    }

    @Override
    public List<float[]> embedDocuments(List<String> texts) {
        // One request per batch: far cheaper than one per text (5.2: 30 texts took 481 ms one by one, 34 ms batched).
        // The server also caps how many inputs one request may carry, so larger lists are split.
        List<float[]> vectors = new ArrayList<>(texts.size());
        for (int start = 0; start < texts.size(); start += properties.batchSize()) {
            List<String> batch = texts.subList(start, Math.min(start + properties.batchSize(), texts.size()));
            float[][] embedded = restClient.post()
                    .uri("/embed")
                    .body(Map.of("inputs", batch))
                    .retrieve()
                    .body(float[][].class);
            if (embedded == null || embedded.length != batch.size()) {
                throw new IllegalStateException("Embedding server returned " + (embedded == null ? 0 : embedded.length)
                        + " vectors for " + batch.size() + " texts");
            }
            vectors.addAll(List.of(embedded));
        }
        return vectors;
    }

    @Override
    public float[] embedQuery(String query) {
        // Qwen3-Embedding is trained to receive queries with an instruction describing the task
        return embedDocuments(List.of("Instruct: " + properties.queryInstruction() + "\nQuery: " + query)).getFirst();
    }
}
