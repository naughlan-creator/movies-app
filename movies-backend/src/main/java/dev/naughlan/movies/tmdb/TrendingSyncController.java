package dev.naughlan.movies.tmdb;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Admin", description = "Operations for moderators (ADMIN role)")
@RestController
@RequestMapping("/api/v1/admin")
public class TrendingSyncController {

    private final TrendingMoviesSync trendingMoviesSync;
    private final TmdbProperties properties;

    TrendingSyncController(TrendingMoviesSync trendingMoviesSync, TmdbProperties properties) {
        this.trendingMoviesSync = trendingMoviesSync;
        this.properties = properties;
    }

    @Operation(summary = "Refresh this week's trending movies from TMDB now")
    @ApiResponse(responseCode = "200", description = "Sync finished")
    @ApiResponse(responseCode = "403", description = "Not an admin",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "502", description = "TMDB failed or timed out",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "503", description = "TMDB_API_TOKEN is not configured",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @SecurityRequirement(name = "keycloak")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/trending-sync")
    public TrendingSyncResponse syncTrending() {
        if (!properties.hasApiToken()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "TMDB_API_TOKEN is not configured");
        }
        try {
            return new TrendingSyncResponse(trendingMoviesSync.syncTrendingMovies());
        } catch (RestClientException e) {
            // 502 Bad Gateway: our server is fine, the upstream service it depends on failed
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "TMDB request failed");
        }
    }

    public record TrendingSyncResponse(int synced) {
    }
}
