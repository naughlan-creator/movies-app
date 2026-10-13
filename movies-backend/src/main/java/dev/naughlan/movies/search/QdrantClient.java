package dev.naughlan.movies.search;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.annotation.JsonProperty;

@Component
class QdrantClient {

        /**
         * One stored movie: its vector plus the payload returned with search results.
         */
        record Point(String id, float[] vector, Map<String, Object> payload) {
        }

        /** One search hit. */
        record ScoredPoint(String id, double score, Map<String, Object> payload) {
        }

        // Qdrant wraps every answer in {"result": ..., "status": ..., "time": ...}
        private record Response<T>(T result) {
        }

        private record Exists(boolean exists) {
        }

        private record QueryResult(List<ScoredPoint> points) {
        }

        private record AliasList(List<Alias> aliases) {
        }

        private record Alias(@JsonProperty("alias_name") String aliasName,
                        @JsonProperty("collection_name") String collectionName) {
        }

        private final RestClient restClient;

        QdrantClient(RestClient.Builder builder, SearchProperties properties) {
                SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
                requestFactory.setConnectTimeout(Duration.ofSeconds(2));
                requestFactory.setReadTimeout(Duration.ofSeconds(10));
                this.restClient = builder.clone()
                                .baseUrl(properties.qdrantUrl())
                                .requestFactory(requestFactory)
                                .build();
        }

        boolean collectionExists(String collection) {
                Response<Exists> response = restClient.get()
                                .uri("/collections/{name}/exists", collection)
                                .retrieve()
                                .body(new ParameterizedTypeReference<Response<Exists>>() {
                                });
                return response != null && response.result().exists();
        }

        void createCollection(String collection, int dimensions) {
                restClient.put()
                                .uri("/collections/{name}", collection)
                                .body(Map.of("vectors", Map.of("size", dimensions, "distance", "Cosine")))
                                .retrieve()
                                .toBodilessEntity();
        }

        /**
         * An index on a payload field keeps filtering on it fast as the collection
         * grows.
         */
        void createKeywordIndex(String collection, String field) {
                restClient.put()
                                .uri("/collections/{name}/index", collection)
                                .body(Map.of("field_name", field, "field_schema", "keyword"))
                                .retrieve()
                                .toBodilessEntity();
        }

        /** The collection an alias points to, or null if the alias doesn't exist. */
        String aliasTarget(String alias) {
                Response<AliasList> response = restClient.get()
                                .uri("/aliases")
                                .retrieve()
                                .body(new ParameterizedTypeReference<Response<AliasList>>() {
                                });
                if (response == null) {
                        return null;
                }
                return response.result().aliases().stream()
                                .filter(a -> a.aliasName().equals(alias))
                                .map(Alias::collectionName)
                                .findFirst()
                                .orElse(null);
        }

        /**
         * Points the alias at a collection. Both actions run in one request,
         * atomically: searches never see
         * a moment without the alias. This is how a new index replaces the old one
         * without downtime.
         */
        void switchAlias(String alias, String collection, boolean aliasExists) {
                List<Map<String, Object>> actions = aliasExists
                                ? List.of(Map.of("delete_alias", Map.of("alias_name", alias)),
                                                Map.of("create_alias",
                                                                Map.of("alias_name", alias, "collection_name",
                                                                                collection)))
                                : List.of(Map.of("create_alias",
                                                Map.of("alias_name", alias, "collection_name", collection)));
                restClient.post()
                                .uri("/collections/aliases")
                                .body(Map.of("actions", actions))
                                .retrieve()
                                .toBodilessEntity();
        }

        /**
         * Inserts or replaces points by id: running it twice for the same movie is
         * harmless.
         */
        void upsert(String collection, List<Point> points) {
                restClient.put()
                                .uri("/collections/{name}/points?wait=true", collection)
                                .body(Map.of("points", points))
                                .retrieve()
                                .toBodilessEntity();
        }

        List<ScoredPoint> query(String collection, float[] vector, int limit, Map<String, Object> filter) {
                Map<String, Object> body = filter == null
                                ? Map.of("query", vector, "limit", limit, "with_payload", true)
                                : Map.of("query", vector, "limit", limit, "with_payload", true, "filter", filter);
                Response<QueryResult> response = restClient.post()
                                .uri("/collections/{name}/points/query", collection)
                                .body(body)
                                .retrieve()
                                .body(new ParameterizedTypeReference<Response<QueryResult>>() {
                                });
                return response == null ? List.of() : response.result().points();
        }

        // ---- Used by the reconciler ----

        /** A stored point without its vector. */
        record StoredPoint(String id, Map<String, Object> payload) {
        }

        private record ScrollResult(List<StoredPoint> points, @JsonProperty("next_page_offset") Object nextPageOffset) {
        }

        /**
         * Every point in the collection with the requested payload fields, page by
         * page. Vectors stay on the server: they're big and not needed.
         */
        List<StoredPoint> scroll(String collection, List<String> payloadFields) {
                List<StoredPoint> all = new ArrayList<>();
                Object offset = null;
                do {
                        Map<String, Object> body = new HashMap<>();
                        body.put("limit", 256);
                        body.put("with_payload", payloadFields);
                        body.put("with_vector", false);
                        if (offset != null) {
                                body.put("offset", offset);
                        }
                        Response<ScrollResult> response = restClient.post()
                                        .uri("/collections/{name}/points/scroll", collection)
                                        .body(body)
                                        .retrieve()
                                        .body(new ParameterizedTypeReference<Response<ScrollResult>>() {
                                        });
                        if (response == null) {
                                break;
                        }
                        all.addAll(response.result().points());
                        offset = response.result().nextPageOffset();
                } while (offset != null);
                return all;
        }

        void delete(String collection, List<String> ids) {
                restClient.post()
                                .uri("/collections/{name}/points/delete?wait=true", collection)
                                .body(Map.of("points", ids))
                                .retrieve()
                                .toBodilessEntity();
        }

        void deleteCollection(String collection) {
                restClient.delete()
                                .uri("/collections/{name}", collection)
                                .retrieve()
                                .toBodilessEntity();
        }
}