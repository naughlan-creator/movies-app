package dev.naughlan.movies.review;

public record  ReviewResponse(String id, String body) {
    public static ReviewResponse from(Review review) {
        return new ReviewResponse(review.getId().toHexString(), review.getBody());
    }    
}
