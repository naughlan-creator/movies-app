package dev.naughlan.movies.movie;

import dev.naughlan.movies.common.ResourceNotFoundException;

public class MovieNotFoundException extends ResourceNotFoundException {
    public MovieNotFoundException(String imdbId) {
        super("Movie not found", "No movie with IMDb id " + imdbId);
    }
}
