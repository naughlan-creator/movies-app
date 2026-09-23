package dev.naughlan.movies;

import java.util.List;
import java.util.Optional;

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
    public Optional<Movie> singleMovie(String imdbId) { return movieRepository.findMovieByImdbId(imdbId); }
}
