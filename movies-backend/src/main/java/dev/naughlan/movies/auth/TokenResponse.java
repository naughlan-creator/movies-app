package dev.naughlan.movies.auth;

public record TokenResponse(String accessToken, String tokenType, long expiresIn) {
}