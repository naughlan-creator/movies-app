package dev.naughlan.movies.movie;

import java.util.function.Supplier;

import org.springframework.stereotype.Component;

import dev.naughlan.movies.common.JsonCache;
import dev.naughlan.movies.common.PageResponse;
import tools.jackson.core.type.TypeReference;

/**
 * Caches the movie API's responses. Any change to movie data (the TMDB sync, a new review count)
 * calls {@link #invalidateAll()}: movie writes are rare, so dropping everything is simpler and safer
 * than working out exactly which pages a change affects.
 */
@Component
public class MovieCache {

    private static final String NAMESPACE = "movies";
    private static final TypeReference<PageResponse<MovieSummaryResponse>> PAGE_TYPE = new TypeReference<>() {
    };

    private final JsonCache cache;

    MovieCache(JsonCache cache) {
        this.cache = cache;
    }

    PageResponse<MovieSummaryResponse> list(int page, int size, Supplier<PageResponse<MovieSummaryResponse>> loader) {
        return cache.getOrLoad(key("list:" + page + ":" + size), PAGE_TYPE, loader);
    }

    MovieDetailResponse detail(String imdbId, Supplier<MovieDetailResponse> loader) {
        return cache.getOrLoad(key("detail:" + imdbId), MovieDetailResponse.class, loader);
    }

    public void invalidateAll() {
        cache.nextGeneration(NAMESPACE);
    }

    // e.g. movies:v7:detail:tt3915174. After invalidateAll() the generation is 8, so v7 keys are never read again.
    private String key(String suffix) {
        return NAMESPACE + ":v" + cache.generation(NAMESPACE) + ":" + suffix;
    }
}
