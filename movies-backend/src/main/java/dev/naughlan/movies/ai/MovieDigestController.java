package dev.naughlan.movies.ai;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Movies")
@RestController
@RequestMapping("/api/v1/movies")
class MovieDigestController {

    private final ReviewDigestService digestService;

    MovieDigestController(ReviewDigestService digestService) {
        this.digestService = digestService;
    }

    // Reads the stored digest only: this endpoint never calls the model, so it's fast, free and safe to make public
    @Operation(summary = "The AI digest of a movie's reviews")
    @ApiResponse(responseCode = "200", description = "Digest found")
    @ApiResponse(responseCode = "404", description = "No digest generated yet",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @GetMapping("/{imdbId}/digest")
    public DigestResponse digest(@PathVariable String imdbId) {
        return digestService.find(imdbId)
                .map(DigestResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No digest for this movie yet"));
    }
}