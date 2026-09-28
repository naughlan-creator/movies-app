package dev.naughlan.movies.common;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI moviesOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Movie Gold API")
                .version("v1")
                .description("This week's trending movies from TMDB, with synopsis, top cast, viewer reviews and reviews written in this app."));
    }
}