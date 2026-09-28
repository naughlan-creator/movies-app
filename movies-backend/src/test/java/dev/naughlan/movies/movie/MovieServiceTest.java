package dev.naughlan.movies.movie;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
        when(repository.findByTrendingRankNotNull(any(Pageable.class))).thenReturn(Page.empty());
        when(repository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(new Movie())));

        assertThat(service.allMovies(0, 10).getContent()).hasSize(1);
    }
}