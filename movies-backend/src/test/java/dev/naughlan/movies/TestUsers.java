package dev.naughlan.movies;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import dev.naughlan.movies.security.CurrentUser;

/**
 * The same two users as the dev Keycloak realm: movie_fan_43 is an admin, movie_fan_42 is a regular user.
 * Requests get a Keycloak-shaped access token without Keycloak running: jwt() skips decoding entirely.
 */
public final class TestUsers {

    public static final String MOVIE_FAN_43_ID = "11111111-1111-4111-8111-000000000043";
    public static final String MOVIE_FAN_42_ID = "11111111-1111-4111-8111-000000000042";

    public static final CurrentUser MOVIE_FAN_43 = new CurrentUser(MOVIE_FAN_43_ID, "movie_fan_43", Set.of("USER", "ADMIN"));
    public static final CurrentUser MOVIE_FAN_42 = new CurrentUser(MOVIE_FAN_42_ID, "movie_fan_42", Set.of("USER"));

    private TestUsers() {
    }

    public static RequestPostProcessor movieFan43() {
        return as(MOVIE_FAN_43);
    }

    public static RequestPostProcessor movieFan42() {
        return as(MOVIE_FAN_42);
    }

    public static RequestPostProcessor as(CurrentUser user) {
        String[] roles = user.roles().toArray(String[]::new);
        List<GrantedAuthority> authorities = Arrays.stream(roles)
                .<GrantedAuthority>map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
        return jwt()
                .jwt(token -> token
                        .subject(user.id())
                        .claim("preferred_username", user.username())
                        .claim("roles", List.of(roles)))
                .authorities(authorities);
    }
}
