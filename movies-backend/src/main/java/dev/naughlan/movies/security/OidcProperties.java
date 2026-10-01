package dev.naughlan.movies.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Where tokens come from and who they must be for.
 *
 * @param issuerUri the exact "iss" value tokens must carry (the public Keycloak URL)
 * @param jwkSetUri where to fetch Keycloak's public signing keys; can be an internal URL
 * @param audience  the "aud" value that marks a token as issued for this API
 */
@ConfigurationProperties("app.security.oidc")
public record OidcProperties(String issuerUri, String jwkSetUri, String audience) {
}
