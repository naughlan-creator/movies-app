package dev.naughlan.movies.security;

import java.util.List;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Verifies access tokens issued by Keycloak. The API holds no signing secret:
 * it checks signatures with Keycloak's public keys.
 */
@Configuration
@EnableConfigurationProperties(OidcProperties.class)
public class JwtDecoderConfig {

    @Bean
    public JwtDecoder jwtDecoder(OidcProperties oidc) {
        // Keys are fetched (and cached) on the first token, so the API still starts while Keycloak is down.
        // Pinning RS256 rejects "none" and algorithm-confusion tricks.
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(oidc.jwkSetUri())
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .build();
        decoder.setJwtValidator(tokenValidator(oidc.issuerUri(), oidc.audience()));
        return decoder;
    }

    // Signature alone isn't enough: the token must also be current, from our realm, and meant for this API
    static OAuth2TokenValidator<Jwt> tokenValidator(String issuer, String audience) {
        OAuth2TokenValidator<Jwt> audienceValidator = new JwtClaimValidator<List<String>>(
                JwtClaimNames.AUD, aud -> aud != null && aud.contains(audience));
        return new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(issuer), audienceValidator);
    }
}
