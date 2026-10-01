package dev.naughlan.movies.common;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import dev.naughlan.movies.security.OidcProperties;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.OAuthFlow;
import io.swagger.v3.oas.models.security.OAuthFlows;
import io.swagger.v3.oas.models.security.Scopes;
import io.swagger.v3.oas.models.security.SecurityScheme;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI moviesOpenApi(OidcProperties oidc) {
        String keycloak = oidc.issuerUri() + "/protocol/openid-connect";
        return new OpenAPI()
            .info(new Info()
                    .title("Movie Gold API")
                    .version("v1")
                    .description("This week's trending movies from TMDB, with synopsis, top cast, viewer reviews and reviews written in this app."))
            .components(new Components()
                    // Swagger UI's "Authorize" button logs in through Keycloak, like the React app does
                    .addSecuritySchemes("keycloak", new SecurityScheme()
                        .type(SecurityScheme.Type.OAUTH2)
                        .description("Log in with Keycloak (Authorization Code + PKCE)")
                        .flows(new OAuthFlows().authorizationCode(new OAuthFlow()
                            .authorizationUrl(keycloak + "/auth")
                            .tokenUrl(keycloak + "/token")
                            .scopes(new Scopes().addString("openid", "Sign in with OpenID Connect"))))));
    }
}
