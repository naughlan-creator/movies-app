package dev.naughlan.movies.search;

import java.util.List;

import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Tag(name = "Movies")
@RestController
@RequestMapping("/api/v1/movies")
class SearchController {

    private final SearchService searchService;

    SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @Operation(summary = "Find movies by meaning, e.g. \"a slow, sad film about a family\"")
    @ApiResponse(responseCode = "200", description = "Best matches first")
    @ApiResponse(responseCode = "400", description = "Missing or too long query",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "503", description = "Search is switched off or its services are down",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @GetMapping("/search")
    public List<SearchService.Hit> search(
            @Parameter(description = "What you'd like to watch, in your own words") @RequestParam @NotBlank @Size(max = 200) String q,
            @RequestParam(defaultValue = "10") @Min(1) @Max(20) int limit) {
        return searchService.search(q, limit);
    }

    @Operation(summary = "Movies similar to this one (\"More like this\")")
    @ApiResponse(responseCode = "200", description = "Most similar first; empty if the movie isn't indexed yet")
    @ApiResponse(responseCode = "404", description = "No movie with that IMDb id",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "503", description = "Search is switched off or its services are down",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @GetMapping("/{imdbId}/similar")
    public List<SearchService.Hit> similar(@PathVariable String imdbId,
                                           @RequestParam(defaultValue = "6") @Min(1) @Max(20) int limit) {
        return searchService.similar(imdbId, limit);
    }
}