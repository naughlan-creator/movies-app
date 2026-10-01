package dev.naughlan.movies.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class CurrentUserTest {

    private static Jwt.Builder token() {
        return Jwt.withTokenValue("token").header("alg", "RS256")
                .subject("11111111-1111-4111-8111-000000000043")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300));
    }

    @Test
    void readsIdentityAndRolesFromKeycloakClaims() {
        Jwt jwt = token()
                .claim("preferred_username", "movie_fan_43")
                .claim("roles", List.of("USER", "ADMIN"))
                .build();

        CurrentUser user = CurrentUser.from(jwt);

        assertThat(user.id()).isEqualTo("11111111-1111-4111-8111-000000000043");
        assertThat(user.username()).isEqualTo("movie_fan_43");
        assertThat(user.roles()).containsExactlyInAnyOrder("USER", "ADMIN");
        assertThat(user.isAdmin()).isTrue();
    }

    @Test
    void missingClaimsDegradeSafely() {
        CurrentUser user = CurrentUser.from(token().build());

        assertThat(user.username()).isEqualTo(user.id());
        assertThat(user.roles()).isEmpty();
        assertThat(user.isAdmin()).isFalse();
    }
}
