package dev.naughlan.movies.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateReviewRequest(
    @NotBlank(message = "Review can't be empty")
    @Size(max = 2000, message = "Review can be at most 2000 characters")
    String reviewBody,

    @NotBlank(message = "ImdbId is required")
    @Pattern(regexp = "tt\\d{7,10}", message = "ImdbId must look like tt1234567")
    String imdbId){

}