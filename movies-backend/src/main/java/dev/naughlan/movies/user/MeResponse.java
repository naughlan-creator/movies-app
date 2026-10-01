package dev.naughlan.movies.user;

import java.util.Set;

import dev.naughlan.movies.security.CurrentUser;

public record MeResponse(String id, String username, Set<String> roles) {

    public static MeResponse from(CurrentUser user) {
        return new MeResponse(user.id(), user.username(), user.roles());
    }
}
