package dev.naughlan.movies.search;

import java.util.List;

import dev.naughlan.movies.ai.ReviewDigest;
import dev.naughlan.movies.movie.Movie;

final class MovieDocument {

    private MovieDocument() {
    }

    static String text(Movie movie, ReviewDigest digest) {
        StringBuilder text = new StringBuilder(movie.getTitle() == null ? "" : movie.getTitle());
        if (movie.getReleaseDate() != null && movie.getReleaseDate().length() >= 4) {
            text.append(" (").append(movie.getReleaseDate(), 0, 4).append(')');
        }
        text.append('\n');
        List<String> genres = movie.getGenres() == null ? List.of() : movie.getGenres();
        if (!genres.isEmpty()) {
            text.append("Genres: ").append(String.join(", ", genres)).append('\n');
        }
        if (movie.getOverview() != null && !movie.getOverview().isBlank()) {
            text.append(movie.getOverview()).append('\n');
        }
        // Too-few-reviews digests say nothing about the movie, so they'd only add noise
        if (digest != null && digest.sentiment() != ReviewDigest.Sentiment.UNKNOWN) {
            text.append("What viewers say: ").append(digest.verdict()).append('\n');
            if (!digest.liked().isEmpty()) {
                text.append("Liked: ").append(String.join("; ", digest.liked())).append('\n');
            }
            if (!digest.disliked().isEmpty()) {
                text.append("Disliked: ").append(String.join("; ", digest.disliked())).append('\n');
            }
        }
        return text.toString().strip();
    }
}