package dev.naughlan.movies.review;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateReviewRequest(
    @Schema(description = "The review text", example = "Loved the villain. Surprisingly emotional for an animated film.")
    @NotBlank(message = "Review can't be empty")
    @Size(max = 2000, message = "Review can be at most 2000 characters")
    String reviewBody,

    @Schema(description = "IMDb id of the movie being reviewed", example = "tt3915174")
    @NotBlank(message = "ImdbId is required")
    @Pattern(regexp = "tt\\d{7,10}", message = "ImdbId must look like tt1234567")
    String imdbId){

}