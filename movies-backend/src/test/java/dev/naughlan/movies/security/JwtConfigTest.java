package dev.naughlan.movies.security;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtConfigTest {

    @Test
    void refusesToStartWithAKeyShorterThan256Bits() {
        JwtProperties properties = new JwtProperties("c2hvcnQta2V5", "movie-gold-api", Duration.ofMinutes(15)); // "short-key"

        assertThatThrownBy(() -> new JwtConfig().jwtSigningKey(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("256 bits");
    }
}