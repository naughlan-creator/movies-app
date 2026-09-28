package dev.naughlan.movies.movie;

import java.util.List;
import java.util.Objects;

public record MovieSummaryResponse(
    String imdbId,
    String title,
    String releaseDate,
    Integer runtime,
    Double rating,
    String overview,
    List<CastMember> cast,
    String poster,
    List<String> backdrops,
    String trailerLink,
    Integer trendingRank
) {
    public static MovieSummaryResponse from(Movie movie) {
        return new MovieSummaryResponse(
            movie.getImdbId(), 
            movie.getTitle(),
            movie.getReleaseDate(),
            movie.getRuntime(),
            movie.getRating(),
            movie.getOverview(),
            Objects.requireNonNullElse(movie.getCast(), List.of()),
            movie.getPoster(),
            Objects.requireNonNullElse(movie.getBackdrops(), List.of()),
            movie.getTrailerLink(),
            movie.getTrendingRank());
    }
}
