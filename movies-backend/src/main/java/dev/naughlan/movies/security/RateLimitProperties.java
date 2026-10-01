package dev.naughlan.movies.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param enabled          turn rate limiting on or off
 * @param readsPerMinute   GET requests per client per minute
 * @param writesPerMinute  POST/PUT/PATCH/DELETE requests per client per minute
 * @param maxTrackedClients upper bound on remembered clients, so many IPs can't exhaust memory
 */
@ConfigurationProperties("app.rate-limit")
public record RateLimitProperties(boolean enabled, int readsPerMinute, int writesPerMinute, long maxTrackedClients) {
}
