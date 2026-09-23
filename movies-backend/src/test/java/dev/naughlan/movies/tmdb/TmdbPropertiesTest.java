package dev.naughlan.movies.tmdb;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TmdbPropertiesTest {

    @Test
    void toStringNeverContainsTheToken() {
        TmdbProperties properties = new TmdbProperties("https://api", "https://img", "super-secret-token", 10);

        assertThat(properties.toString()).doesNotContain("super-secret-token").contains("apiToken=****");
    }

    @Test
    void blankTokenCountsAsMissing() {
        assertThat(new TmdbProperties("a", "b", "", 10).hasApiToken()).isFalse();
        assertThat(new TmdbProperties("a", "b", null, 10).hasApiToken()).isFalse();
    }
}
