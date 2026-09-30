package dev.naughlan.movies.review;

import java.time.Instant;

public record ReviewResponse(String id, String body, String author, Instant createdAt) {
    public static ReviewResponse from(Review review) {
        return new ReviewResponse(review.getId().toHexString(), review.getBody(), review.getAuthor(), review.getCreatedAt());
    }    
}
