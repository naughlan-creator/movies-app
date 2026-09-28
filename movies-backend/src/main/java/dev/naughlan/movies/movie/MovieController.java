package dev.naughlan.movies.movie;

import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
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

@Tag(name= "Movies", description = "This week's trending movies and full movie details")
@RestController
@RequestMapping("/api/v1/movies")
public class MovieController {
    private final MovieService movieService;

    public MovieController(MovieService movieService) {
        this.movieService = movieService;
    }

    @Operation(
        summary = "List this week's trending movies",
        description = "Paged and ordered by trending rank. Falls back to every stored movie, by title, if the TMDB sync has never run.")
    @GetMapping
    public PageResponse<MovieSummaryResponse> getAllMovies(
            @Parameter(description = "Zero-based page number") @RequestParam(defaultValue = "0") @Min(0) int page,
            @Parameter(description = "Movies per page") @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size) {
        return PageResponse.from(movieService.allMovies(page, size).map(MovieSummaryResponse::from));
    }
    
    @Operation(summary = "Get one movie with its cast, viewer reviews and reviews written in this app")
    @ApiResponse(responseCode = "200", description = "Movie found")
    @ApiResponse(responseCode = "404", description = "No movie with that IMDb id",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @GetMapping("/{imdbId}")
    public MovieDetailResponse getSingleMovie(
        @Parameter(description = "IMDb id of the movie", example = "tt3915174") @PathVariable String imdbId
    ) {
        return MovieDetailResponse.from(movieService.singleMovie(imdbId));
    }
}
