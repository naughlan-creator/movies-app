package dev.naughlan.movies.tmdb;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Response of GET /movie/{id}?append_to_response=credits,videos,reviews.
 * TMDB uses snake_case JSON, so @JsonProperty maps it onto camelCase Java names.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TmdbMovieDetails(
        int id,
        @JsonProperty("imdb_id") String imdbId,
        String title,
        String overview,
        @JsonProperty("release_date") String releaseDate,
        Integer runtime,
        @JsonProperty("vote_average") Double voteAverage,
        @JsonProperty("poster_path") String posterPath,
        @JsonProperty("backdrop_path") String backdropPath,
        List<Genre> genres,
        Credits credits,
        Videos videos,
        Reviews reviews) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Genre(String name) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Credits(List<CastEntry> cast) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CastEntry(String name, String character, @JsonProperty("profile_path") String profilePath, Integer order) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Videos(List<Video> results) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Video(String key, String site, String type, Boolean official) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Reviews(List<ReviewEntry> results) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ReviewEntry(
            String author,
            @JsonProperty("author_details") AuthorDetails authorDetails,
            String content,
            @JsonProperty("created_at") String createdAt,
            String url) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AuthorDetails(Double rating) {
    }
}
