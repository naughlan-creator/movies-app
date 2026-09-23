package dev.naughlan.movies;

// A review written on TMDB by someone who watched the film.
// Kept separate from Review, which holds reviews written in this app.
public record AudienceReview(String author, Double rating, String content, String url, String createdAt) {
}
