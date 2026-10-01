package dev.naughlan.movies.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * The signature is checked by Nimbus with Keycloak's public key. These tests cover what comes after:
 * a correctly signed token must still be current, from our realm, and meant for this API.
 */
class JwtDecoderConfigTest {

    private static final String ISSUER = "http://localhost:18180/realms/movie-gold";
    private static final String AUDIENCE = "movie-gold-api";

    private final OAuth2TokenValidator<Jwt> validator = JwtDecoderConfig.tokenValidator(ISSUER, AUDIENCE);

    private static Jwt.Builder validToken() {
        Instant now = Instant.now();
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .issuer(ISSUER)
                .audience(List.of(AUDIENCE, "account"))
                .subject("some-keycloak-user-id")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300));
    }

    @Test
    void acceptsATokenIssuedForThisApi() {
        assertThat(validator.validate(validToken().build()).hasErrors()).isFalse();
    }

    @Test
    void rejectsATokenIssuedForAnotherApi() {
        Jwt token = validToken().audience(List.of("account")).build();

        assertThat(validator.validate(token).hasErrors()).isTrue();
    }

    @Test
    void rejectsATokenFromAnotherRealm() {
        Jwt token = validToken().issuer("http://localhost:18180/realms/someone-else").build();

        assertThat(validator.validate(token).hasErrors()).isTrue();
    }

    @Test
    void rejectsAnExpiredToken() {
        Instant anHourAgo = Instant.now().minusSeconds(3600);
        Jwt token = validToken().issuedAt(anHourAgo).expiresAt(anHourAgo.plusSeconds(300)).build();

        assertThat(validator.validate(token).hasErrors()).isTrue();
    }
}
