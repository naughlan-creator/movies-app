package dev.naughlan.movies.security;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import dev.naughlan.movies.common.RateLimitExceededException;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Token-bucket rate limiting per client: the authenticated user if there is one, otherwise the IP address.
 * Runs inside the security filter chain, after the bearer token has been read, so users are recognised.
 *
 * Buckets live in this JVM's memory. With several API instances each would count separately;
 * a shared store (e.g. Redis via bucket4j-redis) fixes that when the app is scaled out.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitProperties properties;
    private final HandlerExceptionResolver exceptionResolver;
    // Bounded and self-expiring: an unbounded map keyed by IP would itself be a memory-exhaustion attack
    private final Cache<String, Bucket> buckets;

    public RateLimitFilter(RateLimitProperties properties, HandlerExceptionResolver exceptionResolver) {
        this.properties = properties;
        this.exceptionResolver = exceptionResolver;
        this.buckets = Caffeine.newBuilder()
                .maximumSize(properties.maxTrackedClients())
                .expireAfterAccess(Duration.ofMinutes(10))
                .build();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/") || HttpMethod.OPTIONS.matches(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        boolean write = isWrite(request.getMethod());
        String key = clientKey(request) + (write ? "|write" : "|read");
        int perMinute = write ? properties.writesPerMinute() : properties.readsPerMinute();
        Bucket bucket = buckets.get(key, k -> newBucket(perMinute));

        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            response.setHeader("X-RateLimit-Remaining", String.valueOf(probe.getRemainingTokens()));
            chain.doFilter(request, response);
            return;
        }

        long retryAfterSeconds = Math.max(1, (long) Math.ceil(probe.getNanosToWaitForRefill() / (double) TimeUnit.SECONDS.toNanos(1)));
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
        // Same ProblemDetail format as every other error (see GlobalExceptionHandler)
        exceptionResolver.resolveException(request, response, null, new RateLimitExceededException(retryAfterSeconds));
    }

    private static boolean isWrite(String method) {
        return !(HttpMethod.GET.matches(method) || HttpMethod.HEAD.matches(method));
    }

    private static String clientKey(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)) {
            return "user:" + authentication.getName();
        }
        // Behind a reverse proxy this is the real client IP only if forwarded headers are trusted
        // (server.forward-headers-strategy), and only from known proxies, so clients can't spoof it.
        return "ip:" + request.getRemoteAddr();
    }

    private static Bucket newBucket(int perMinute) {
        return Bucket.builder()
                .addLimit(limit -> limit.capacity(perMinute).refillGreedy(perMinute, Duration.ofMinutes(1)))
                .build();
    }
}
