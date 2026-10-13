package dev.naughlan.movies.search;

import java.util.List;

public interface EmbeddingClient {

    List<float[]> embedDocuments(List<String> texts);

    float[] embedQuery(String query);
}
