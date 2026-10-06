package dev.naughlan.movies.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;

import dev.naughlan.movies.ai.ReviewDigestService.DigestPreview;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Admin", description = "Operations for moderators (ADMIN role)")
@RestController
@RequestMapping("/api/v1/admin/movies")
class ReviewDigestController {

    private static final Logger log = LoggerFactory.getLogger(ReviewDigestController.class);

    private final ReviewDigestService digestService;
    private final AiProperties properties;

    ReviewDigestController(ReviewDigestService digestService, AiProperties properties) {
        this.digestService = digestService;
        this.properties = properties;
    }

    @Operation(summary = "Generate an AI digest of a movie's reviews (not saved yet)")
    @ApiResponse(responseCode = "200", description = "Digest generated")
    @ApiResponse(responseCode = "403", description = "Not an admin", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "502", description = "The AI service failed", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @ApiResponse(responseCode = "503", description = "ANTHROPIC_API_KEY is not configured", content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @SecurityRequirement(name = "keycloak")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{imdbId}/digest-preview")
    public DigestPreview preview(@PathVariable String imdbId) {
        if (!properties.hasApiKey()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "ANTHROPIC_API_KEY is not configured");
        }
        try {
            return digestService.preview(imdbId);
        } catch (AnthropicServiceException e) {
            // The API answered with an error status (bad request, auth, rate limit,
            // overloaded...)
            log.warn("Claude API returned {} for {}", e.statusCode(), imdbId);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI request failed");
        } catch (AnthropicIoException e) {
            // We never got an answer: network error or timeout (after the SDK's retries)
            log.warn("Claude API unreachable for {}: {}", imdbId, e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI service unreachable");
        } catch (UnsafeDigestException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI output failed safety checks");
        }
    }
}