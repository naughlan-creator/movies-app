package dev.naughlan.movies.tmdb;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Response of GET /trending/movie/week. Only the fields we use are declared;
 * ignoreUnknown lets TMDB add fields without breaking us.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TmdbTrendingPage(List<Result> results) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Result(int id, String title) {
    }
}
