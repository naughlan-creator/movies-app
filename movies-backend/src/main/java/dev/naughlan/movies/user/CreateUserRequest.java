package dev.naughlan.movies.user;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
        @Schema(example = "movie_fan_42")
        @NotBlank(message = "Username is required")
        @Size(min = 3, max = 30, message = "Username must be 3 to 30 characters")
        @Pattern(regexp = "[a-zA-Z0-9_.-]+", message = "Username may only contain letters, digits, _ . and -")
        String username,

        @Schema(format = "password", example = "correct horse battery staple")
        @NotNull(message = "Password is required")
        @Size(min = 15, max = 64, message = "Password must be 15 to 64 characters")
        String password) {

    // Records print every field in toString(). Never let a password reach a log line.
    @Override
    public String toString() {
        return "CreateUserRequest[username=" + username + ", password=****]";
    }
}