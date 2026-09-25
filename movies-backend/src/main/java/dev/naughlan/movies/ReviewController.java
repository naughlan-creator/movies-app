package dev.naughlan.movies;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.naughlan.movies.dto.CreateReviewRequest;
import dev.naughlan.movies.dto.ReviewResponse;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/reviews")
public class ReviewController {
    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @PostMapping
    public ResponseEntity<ReviewResponse> createReview(@Valid @RequestBody CreateReviewRequest request) {
        Review review = reviewService.createReview(request.reviewBody(), request.imdbId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ReviewResponse.from(review));
    }
}
