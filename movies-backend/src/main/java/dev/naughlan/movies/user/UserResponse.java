package dev.naughlan.movies.user;

import java.time.Instant;
import java.util.Set;

public record UserResponse(String id, String username, Set<Role> roles, Instant createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(user.id(), user.username(), user.roles(), user.createdAt());
    }
}