package dev.naughlan.movies.tmdb;

import dev.naughlan.movies.AudienceReview;
import dev.naughlan.movies.CastMember;
import dev.naughlan.movies.Movie;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Turns TMDB's response shape into our Movie shape. Pure logic, no HTTP or database,
 * so it can be unit tested with plain objects.
 */
@Component
public class TmdbMovieMapper {

    static final int CAST_LIMIT = 3;
    static final int REVIEW_LIMIT = 3;
    static final int REVIEW_SNIPPET_LENGTH = 500;

    private final String imageBaseUrl;

    // Exactly one constructor: with two, Spring can't tell which one to use and startup fails
    public TmdbMovieMapper(TmdbProperties properties) {
        this.imageBaseUrl = properties.imageBaseUrl();
    }

    public Movie toMovie(TmdbMovieDetails details, int trendingRank, Instant syncedAt) {
        Movie movie = new Movie();
        movie.setImdbId(details.imdbId());
        movie.setTmdbId(details.id());
        movie.setTitle(details.title());
        movie.setReleaseDate(details.releaseDate());
        movie.setOverview(details.overview());
        movie.setRuntime(details.runtime());
        movie.setRating(details.voteAverage());
        movie.setPoster(imageUrl("w500", details.posterPath()));
        String backdrop = imageUrl("w1280", details.backdropPath());
        movie.setBackdrops(backdrop == null ? List.of() : List.of(backdrop));
        movie.setGenres(details.genres() == null ? List.of()
                : details.genres().stream().map(TmdbMovieDetails.Genre::name).toList());
        movie.setTrailerLink(trailerLink(details.videos()));
        movie.setCast(topCast(details.credits()));
        movie.setAudienceReviews(topReviews(details.reviews()));
        movie.setTrendingRank(trendingRank);
        movie.setSyncedAt(syncedAt);
        return movie;
    }

    private String imageUrl(String size, String path) {
        return path == null ? null : imageBaseUrl + "/" + size + path;
    }

    // Prefer an official YouTube trailer, fall back to any YouTube trailer
    private String trailerLink(TmdbMovieDetails.Videos videos) {
        if (videos == null || videos.results() == null) {
            return null;
        }
        return videos.results().stream()
                .filter(v -> "YouTube".equals(v.site()) && "Trailer".equals(v.type()) && v.key() != null)
                .min(Comparator.comparing(v -> !Boolean.TRUE.equals(v.official())))
                .map(v -> "https://www.youtube.com/watch?v=" + v.key())
                .orElse(null);
    }

    // TMDB's "order" field is billing order: 0 is the lead actor
    private List<CastMember> topCast(TmdbMovieDetails.Credits credits) {
        if (credits == null || credits.cast() == null) {
            return List.of();
        }
        return credits.cast().stream()
                .sorted(Comparator.comparing(TmdbMovieDetails.CastEntry::order,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .limit(CAST_LIMIT)
                .map(c -> new CastMember(c.name(), c.character(), imageUrl("w185", c.profilePath())))
                .toList();
    }

    // "Top" = highest rated first; unrated reviews go last; ties broken by newest
    private List<AudienceReview> topReviews(TmdbMovieDetails.Reviews reviews) {
        if (reviews == null || reviews.results() == null) {
            return List.of();
        }
        Comparator<TmdbMovieDetails.ReviewEntry> byRating = Comparator.comparing(
                r -> r.authorDetails() == null ? null : r.authorDetails().rating(),
                Comparator.nullsLast(Comparator.reverseOrder()));
        Comparator<TmdbMovieDetails.ReviewEntry> newestFirst = Comparator.comparing(
                TmdbMovieDetails.ReviewEntry::createdAt,
                Comparator.nullsLast(Comparator.reverseOrder()));

        return reviews.results().stream()
                .filter(r -> r.content() != null && !r.content().isBlank())
                .sorted(byRating.thenComparing(newestFirst))
                .limit(REVIEW_LIMIT)
                .map(r -> new AudienceReview(
                        r.author(),
                        r.authorDetails() == null ? null : r.authorDetails().rating(),
                        snippet(r.content()),
                        r.url(),
                        r.createdAt()))
                .toList();
    }

    // Full reviews can be thousands of words; keep a readable snippet and link to the full one
    static String snippet(String content) {
        String text = Objects.requireNonNull(content).replaceAll("\\s+", " ").trim();
        if (text.length() <= REVIEW_SNIPPET_LENGTH) {
            return text;
        }
        int cut = text.lastIndexOf(' ', REVIEW_SNIPPET_LENGTH);
        return text.substring(0, cut > 0 ? cut : REVIEW_SNIPPET_LENGTH) + "…";
    }
}
