package dev.naughlan.movies.movie;

import java.util.List;
import java.util.Objects;

// Reviews written in this app are paged separately: GET /api/v1/movies/{imdbId}/reviews
public record MovieDetailResponse(
    String imdbId,
    String title,
    String releaseDate,
    Integer runtime,
    Double rating,
    List<String> genres,
    String overview,
    List<CastMember> cast,
    String poster,
    List<String> backdrops,
    String trailerLink,
    List<AudienceReview> audienceReviews,
    long reviewCount
) {
    public static MovieDetailResponse from(Movie movie) {
        return new MovieDetailResponse(
            movie.getImdbId(),
            movie.getTitle(),
            movie.getReleaseDate(),
            movie.getRuntime(),
            movie.getRating(),
            Objects.requireNonNullElse(movie.getGenres(), List.of()),
            movie.getOverview(),
            Objects.requireNonNullElse(movie.getCast(), List.of()),
            movie.getPoster(),
            Objects.requireNonNullElse(movie.getBackdrops(), List.of()),
            movie.getTrailerLink(),
            Objects.requireNonNullElse(movie.getAudienceReviews(), List.of()),
            Objects.requireNonNullElse(movie.getReviewCount(), 0L));
    }
}
