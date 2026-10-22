package dev.naughlan.movies.ai;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Typed view of every app.ai.* property in application.properties.
 */
@ConfigurationProperties("app.ai")
public record AiProperties(String provider, String apiKey, String model, long maxTokens, Duration timeout, Digest digest, Price price, OpenModel openModel) {

     /** app.ai.digest.* */
    public record Digest(boolean refreshEnabled, Duration delay, int maxPerRun) {
    }

    public record Price(double inputPerMillionTokens, double outputPerMillionTokens) {
    }

    /** app.ai.open-model.*: a self-hosted model behind an OpenAI-compatible API (the vLLM "llm" service) */
    public record OpenModel(String baseUrl, String model, long maxTokens, Duration timeout) {
    }

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public String toString() {
        return "AiProperties[provider=%s, apiKey=%s, model=%s, maxTokens=%d, timeout=%s, digest=%s, openModel=%s]"
            .formatted(provider, hasApiKey() ? "****" : "<not set>", model, maxTokens, timeout, digest, openModel);
    }
}