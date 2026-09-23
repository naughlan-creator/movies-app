package dev.naughlan.movies.tmdb;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Typed view of every app.tmdb.* property in application.properties.
 */
@ConfigurationProperties("app.tmdb")
public record TmdbProperties(String baseUrl, String imageBaseUrl, String apiToken, int trendingLimit) {

    public boolean hasApiToken() {
        return apiToken != null && !apiToken.isBlank();
    }

    // Records print every field in toString(). Mask the token so it can never leak into logs.
    @Override
    public String toString() {
        return "TmdbProperties[baseUrl=%s, imageBaseUrl=%s, apiToken=%s, trendingLimit=%d]"
                .formatted(baseUrl, imageBaseUrl, hasApiToken() ? "****" : "<not set>", trendingLimit);
    }
}
