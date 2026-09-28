package dev.naughlan.movies.movie;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
public class MovieService {
    private final MovieRepository movieRepository;

    public MovieService(MovieRepository movieRepository) {
        this.movieRepository = movieRepository;
    }

    // This week's trending movies; falls back to everything if the TMDB sync has never run
    public Page<Movie> allMovies(int page, int size) {
        Page<Movie> trending = movieRepository.findByTrendingRankNotNull(
                PageRequest.of(page, size, Sort.by("trendingRank")));
        if (trending.getTotalElements() > 0) {
            return trending;
        }
        // Title alone isn't unique; imdbId breaks ties so pages never overlap
        return movieRepository.findAll(PageRequest.of(page, size, Sort.by("title", "imdbId")));
    }
    public Movie singleMovie(String imdbId) {
        return movieRepository.findMovieByImdbId(imdbId)
                .orElseThrow(() -> new MovieNotFoundException(imdbId));
    }
}
