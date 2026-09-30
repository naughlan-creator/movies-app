package dev.naughlan.movies.review;

import dev.naughlan.movies.common.ResourceNotFoundException;

public class ReviewNotFoundException extends ResourceNotFoundException {
    public ReviewNotFoundException(String reviewId) {
        super("Review not found", "No review with id " + reviewId);
    }
}