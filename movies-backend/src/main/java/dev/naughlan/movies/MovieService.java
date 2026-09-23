package dev.naughlan.movies;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class MovieService {
    @Autowired
    private MovieRepository movieRepository;
    // This week's trending movies; falls back to everything if the TMDB sync has never run
    public List<Movie> allMovies() {
        List<Movie> trending = movieRepository.findByTrendingRankNotNullOrderByTrendingRankAsc();
        return trending.isEmpty() ? movieRepository.findAll() : trending;
    }
    public Optional<Movie> singleMovie(String imdbId) { return movieRepository.findMovieByImdbId(imdbId); }
}
