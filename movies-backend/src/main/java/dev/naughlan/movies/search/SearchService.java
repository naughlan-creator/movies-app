package dev.naughlan.movies.search;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;

import dev.naughlan.movies.movie.Movie;
import dev.naughlan.movies.movie.MovieRepository;
import dev.naughlan.movies.movie.MovieService;
import dev.naughlan.movies.movie.MovieSummaryResponse;

@Service
public class SearchService {

    public record Hit(MovieSummaryResponse movie, double score) {
    }

    private final SearchProperties properties;
    private final EmbeddingClient embeddings;
    private final QdrantClient qdrant;
    private final MovieService movieService;
    private final MovieRepository movieRepository;

    SearchService(SearchProperties properties, EmbeddingClient embeddings, QdrantClient qdrant,
                  MovieService movieService, MovieRepository movieRepository) {
        this.properties = properties;
        this.embeddings = embeddings;
        this.qdrant = qdrant;
        this.movieService = movieService;
        this.movieRepository = movieRepository;
    }

    /** Movies whose meaning is closest to the query, e.g. "a slow, sad film about a family". */
    public List<Hit> search(String query, int limit) {
        requireEnabled();
        try {
            float[] vector = embeddings.embedQuery(query.strip());
            return toHits(qdrant.query(properties.alias(), vector, limit, null));
        } catch (RestClientException e) {
            throw new SearchUnavailableException(e);
        }
    }

    /** "More like this": the movie's own stored vector is the query. */
    public List<Hit> similar(String imdbId, int limit) {
        requireEnabled();
        movieService.singleMovie(imdbId); // 404 for a movie that doesn't exist
        String pointId = MovieIndexer.pointId(imdbId);
        // Ranking is by the movie's vector; the filter only removes the movie itself from the candidates
        Map<String, Object> notItself = Map.of("must_not", List.of(Map.of("has_id", List.of(pointId))));
        try {
            return toHits(qdrant.query(properties.alias(), pointId, limit, notItself));
        } catch (HttpClientErrorException.NotFound e) {
            return List.of(); // a new movie the reconciler hasn't indexed yet
        } catch (RestClientException e) {
            throw new SearchUnavailableException(e);
        }
    }

    private void requireEnabled() {
        if (!properties.enabled()) {
            throw new SearchUnavailableException(null);
        }
    }

    // One MongoDB query for all hits, then back into the order Qdrant ranked them.
    // A movie deleted since it was indexed is simply skipped (the next reconcile removes its vector).
    private List<Hit> toHits(List<QdrantClient.ScoredPoint> points) {
        List<String> imdbIds = points.stream().map(p -> (String) p.payload().get("imdbId")).toList();
        Map<String, Movie> movies = movieRepository.findByImdbIdIn(imdbIds).stream()
                .collect(Collectors.toMap(Movie::getImdbId, Function.identity(), (first, duplicate) -> first));
        return points.stream()
                .filter(p -> movies.containsKey((String) p.payload().get("imdbId")))
                .map(p -> new Hit(MovieSummaryResponse.from(movies.get((String) p.payload().get("imdbId"))), p.score()))
                .toList();
    }
}