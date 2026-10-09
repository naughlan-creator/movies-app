package dev.naughlan.movies.ai;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Typed view of every app.ai.* property in application.properties.
 */
@ConfigurationProperties("app.ai")
public record AiProperties(String apiKey, String model, long maxTokens, Duration timeout, Digest digest, Price price) {

     /** app.ai.digest.* */
    public record Digest(boolean refreshEnabled, Duration delay, int maxPerRun) {
    }

    public record Price(double inputPerMillionTokens, double outputPerMillionTokens) {
    }

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public String toString() {
        return "AiProperties[apiKey=%s, model=%s, maxTokens=%d, timeout=%s, digest=%s]"
                .formatted(hasApiKey() ? "****" : "<not set>", model, maxTokens, timeout, digest);
    }
}