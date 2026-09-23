package dev.naughlan.movies;

public class MovieNotFoundException extends RuntimeException {
    public MovieNotFoundException(String imdbId) {
        super("No movie with IMDb id " + imdbId);
    }
}
