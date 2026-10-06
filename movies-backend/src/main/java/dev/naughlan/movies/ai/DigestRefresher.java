package dev.naughlan.movies.ai;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import dev.naughlan.movies.movie.MovieRepository;

/**
 * The only place that spends money on its own. Guardrails: off by default, a delay to batch bursts of
 * reviews, a maximum per run, and a backoff after failures.
 */
@Component
class DigestRefresher {

    private static final Logger log = LoggerFactory.getLogger(DigestRefresher.class);
    private static final Duration FAILURE_BACKOFF = Duration.ofHours(1);

    private final AiProperties properties;
    private final DigestQueue queue;
    private final ReviewDigestService digestService;
    private final StoredDigestRepository digestRepository;
    private final MovieRepository movieRepository;

    DigestRefresher(AiProperties properties, DigestQueue queue, ReviewDigestService digestService,
                    StoredDigestRepository digestRepository, MovieRepository movieRepository) {
        this.properties = properties;
        this.queue = queue;
        this.digestService = digestService;
        this.digestRepository = digestRepository;
        this.movieRepository = movieRepository;
    }

    // fixedDelay: the next run starts a minute after this one FINISHES, so runs never overlap
    @Scheduled(fixedDelayString = "${app.ai.digest.refresh-interval:1m}")
    void refresh() {
        if (!properties.digest().refreshEnabled() || !properties.hasApiKey()) {
            return;
        }
        queueTrendingMoviesWithoutDigest();

        for (StoredDigest due : queue.due(properties.digest().delay(), properties.digest().maxPerRun())) {
            try {
                StoredDigest fresh = digestService.regenerate(due.imdbId());
                log.info("Regenerated digest for {} ({} reviews, {} in / {} out tokens)",
                        fresh.imdbId(), fresh.reviewsUsed(), fresh.inputTokens(), fresh.outputTokens());
            } catch (RuntimeException e) {
                // One movie failing must not stop the others, or retry every minute at our expense
                log.warn("Digest refresh failed for {}, retrying in {}: {}", due.imdbId(), FAILURE_BACKOFF, e.toString());
                queue.postpone(due.imdbId(), FAILURE_BACKOFF);
            }
        }
    }

    // New trending movies (synced from TMDB at startup) have no review events yet, so nothing would queue them
    private void queueTrendingMoviesWithoutDigest() {
        movieRepository.findByTrendingRankNotNull(PageRequest.of(0, 50)).forEach(movie -> {
            if (!digestRepository.existsById(movie.getImdbId())) {
                queue.markStale(movie.getImdbId());
            }
        });
    }
}