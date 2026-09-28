package dev.naughlan.movies.movie;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MovieServiceTest {

    private final MovieRepository repository = mock(MovieRepository.class);
    private final MovieService service = new MovieService(repository);

    @Test
    void throwsWhenMovieDoesNotExist() {
        when(repository.findMovieByImdbId("tt0000000")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.singleMovie("tt0000000"))
                .isInstanceOf(MovieNotFoundException.class)
                .hasMessageContaining("tt0000000");
    }

    @Test
    void fallsBackToAllMoviesWhenNothingIsTrending() {
        when(repository.findByTrendingRankNotNullOrderByTrendingRankAsc()).thenReturn(List.of());
        when(repository.findAll()).thenReturn(List.of(new Movie()));

        assertThat(service.allMovies()).hasSize(1);
    }
}