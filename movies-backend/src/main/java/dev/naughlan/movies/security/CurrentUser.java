package dev.naughlan.movies.security;

import java.util.List;
import java.util.Set;

import org.springframework.security.oauth2.jwt.Jwt;

/**
 * The caller, as described by a verified Keycloak access token.
 *
 * @param id       the stable, never-reused user id ("sub", a UUID). Use this for ownership.
 * @param username the display name ("preferred_username"). It may change; never use it for ownership.
 * @param roles    realm roles from our "roles" claim, e.g. USER, ADMIN
 */
public record CurrentUser(String id, String username, Set<String> roles) {

    public static CurrentUser from(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        String username = jwt.getClaimAsString("preferred_username");
        return new CurrentUser(
                jwt.getSubject(),
                username != null ? username : jwt.getSubject(),
                roles == null ? Set.of() : Set.copyOf(roles));
    }

    public boolean isAdmin() {
        return roles.contains("ADMIN");
    }
}
