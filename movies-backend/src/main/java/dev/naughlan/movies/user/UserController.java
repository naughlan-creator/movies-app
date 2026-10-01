package dev.naughlan.movies.user;

import org.springframework.http.ProblemDetail;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.naughlan.movies.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Accounts live in Keycloak (sign-up, passwords, roles). The API only reports who the caller is.
 */
@Tag(name = "Users", description = "The logged-in user, as seen by the API")
@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    @Operation(summary = "The currently logged-in user")
    @ApiResponse(responseCode = "200", description = "Identity and roles from the access token")
    @ApiResponse(responseCode = "401", description = "Not logged in",
            content = @Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
    @SecurityRequirement(name = "keycloak")
    @GetMapping("/me")
    public MeResponse me(@AuthenticationPrincipal Jwt jwt) {
        return MeResponse.from(CurrentUser.from(jwt));
    }
}
