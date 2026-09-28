package dev.naughlan.movies.movie;

import java.util.List;
import java.util.Objects;

import dev.naughlan.movies.review.Review;
import dev.naughlan.movies.review.ReviewResponse;

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
    List<ReviewResponse> reviews
) {
    public static MovieDetailResponse from(Movie movie) {
        List<Review> reviews = Objects.requireNonNullElse(movie.getReviewIds(), List.of());

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
            reviews.stream().map(ReviewResponse::from).toList());
    }
}
