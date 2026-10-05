package dev.naughlan.movies.ai;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Typed view of every app.ai.* property in application.properties.
 */
@ConfigurationProperties("app.ai")
public record AiProperties(String apiKey, String model, long maxTokens, Duration timeout) {

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }

    // Never let the key reach a log line: records print every field in toString()
    @Override
    public String toString() {
        return "AiProperties[apiKey=%s, model=%s, maxTokens=%d, timeout=%s]"
                .formatted(hasApiKey() ? "****" : "<not set>", model, maxTokens, timeout);
    }
}