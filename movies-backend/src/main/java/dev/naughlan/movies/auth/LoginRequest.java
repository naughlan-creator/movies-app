package dev.naughlan.movies.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @Schema(example = "movie_fan") @NotBlank String username,
        @Schema(format = "password", example = "correct horse battery staple") @NotBlank String password) {

    @Override
    public String toString() {
        return "LoginRequest[username=" + username + ", password=****]";
    }
}