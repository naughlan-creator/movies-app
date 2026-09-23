package dev.naughlan.movies.tmdb;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(TmdbProperties.class)
public class TmdbConfig {
}
