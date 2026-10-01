package dev.naughlan.movies.movie;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MovieDetailResponseTest {
    @Test
    void neverReturnsNullLists() {
        Movie movie = new Movie();
        movie.setImdbId("tt0000001");
        // cast, genres, backdrops and audienceReviews are left null on purpose

        MovieDetailResponse response = MovieDetailResponse.from(movie);

        assertThat(response.imdbId()).isEqualTo("tt0000001");
        assertThat(response.cast()).isEmpty();
        assertThat(response.genres()).isEmpty();
        assertThat(response.backdrops()).isEmpty();
        assertThat(response.audienceReviews()).isEmpty();
    }
}
