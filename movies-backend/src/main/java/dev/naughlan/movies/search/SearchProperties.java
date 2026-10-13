package dev.naughlan.movies.search;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.search")
public record SearchProperties(
    boolean enabled,
    String embeddingsUrl,
    String qdrantUrl,
    String alias,
    String collection,
    int dimensions,
    int batchSize,
    String queryInstruction) {
    
}
