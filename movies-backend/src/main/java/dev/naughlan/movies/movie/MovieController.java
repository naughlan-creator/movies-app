package dev.naughlan.movies.movie;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/movies")
public class MovieController {
    private final MovieService movieService;

    public MovieController(MovieService movieService) {
        this.movieService = movieService;
    }

    @GetMapping
    public List<MovieSummaryResponse> getAllMovies() {
        return movieService.allMovies().stream()
                .map(MovieSummaryResponse::from)
                .toList();
    }
    
    @GetMapping("/{imdbId}")
    public MovieDetailResponse getSingleMovie(@PathVariable String imdbId) {
        return MovieDetailResponse.from(movieService.singleMovie(imdbId));
    }
}
