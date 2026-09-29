package dev.naughlan.movies.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("app.jwt")
public record JwtProperties(String secret, String issuer, Duration ttl) {

    @Override
    public String toString() {
        return "JwtProperties[secret=****, issuer=" + issuer + ", ttl=" + ttl + "]";
    }
}