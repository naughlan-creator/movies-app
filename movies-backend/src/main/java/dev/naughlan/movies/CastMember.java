package dev.naughlan.movies;

// Embedded inside a Movie document, so it has no @Document or @Id of its own
public record CastMember(String name, String character, String profileUrl) {
}
