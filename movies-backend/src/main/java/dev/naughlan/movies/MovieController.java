package dev.naughlan.movies;

import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/movies")
public class MovieController {
    private final MovieService movieService;

    public MovieController(MovieService movieService) {
        this.movieService = movieService;
    }

    @GetMapping
    public List<Movie> getAllMovies() {
        return movieService.allMovies();
    }
    
    @GetMapping("/{imdbId}")
    public Movie getSingleMovie(@PathVariable String imdbId) {
        return movieService.singleMovie(imdbId);
    }
}
