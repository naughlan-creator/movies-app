package dev.naughlan.movies.movie;

import java.util.List;

import org.springframework.stereotype.Service;

@Service
public class MovieService {
    private final MovieRepository movieRepository;

    public MovieService(MovieRepository movieRepository) {
        this.movieRepository = movieRepository;
    }

    // This week's trending movies; falls back to everything if the TMDB sync has never run
    public List<Movie> allMovies() {
        List<Movie> trending = movieRepository.findByTrendingRankNotNullOrderByTrendingRankAsc();
        return trending.isEmpty() ? movieRepository.findAll() : trending;
    }
    public Movie singleMovie(String imdbId) {
        return movieRepository.findMovieByImdbId(imdbId)
                .orElseThrow(() -> new MovieNotFoundException(imdbId));
    }
}
