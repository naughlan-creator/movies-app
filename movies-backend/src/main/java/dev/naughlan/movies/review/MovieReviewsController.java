package dev.naughlan.movies.review;

import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.naughlan.movies.common.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@Tag(name = "Reviews", description = "Reviews written by users of this app")
@RestController
public class MovieReviewsController {

    private final ReviewService reviewService;

    public MovieReviewsController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @Operation(summary = "A movie's reviews, newest first")
    @ApiResponse(responseCode = "200", description = "A page of reviews")
    @ApiResponse(responseCode = "404", description = "No movie with that IMDb id",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @GetMapping("/api/v1/movies/{imdbId}/reviews")
    public PageResponse<ReviewResponse> reviewsForMovie(
            @Parameter(description = "IMDb id of the movie", example = "tt3915174") @PathVariable String imdbId,
            @Parameter(description = "Zero-based page number") @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Reviews per page") @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size) {
        return PageResponse.from(reviewService.reviewsForMovie(imdbId, page, size).map(ReviewResponse::from));
    }
}
