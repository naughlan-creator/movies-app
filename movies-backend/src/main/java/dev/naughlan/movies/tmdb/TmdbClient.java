package dev.naughlan.movies.tmdb;

import java.time.Duration;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * The only class that talks HTTP to TMDB. Everything else works with Java records.
 */
@Component
public class TmdbClient {

    private final RestClient restClient;

    public TmdbClient(TmdbProperties properties) {
        // Always set timeouts on outbound calls: without them a slow TMDB could hang this thread forever
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(10));

        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                // The token goes in a header, not the URL, so it doesn't end up in proxy or server access logs
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiToken())
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public List<TmdbTrendingPage.Result> trendingThisWeek() {
        TmdbTrendingPage page = restClient.get()
                .uri("/trending/movie/week?language=en-US")
                .retrieve()
                .body(TmdbTrendingPage.class);
        return page == null || page.results() == null ? List.of() : page.results();
    }

    public TmdbMovieDetails movieDetails(int tmdbId) {
        // append_to_response bundles credits, videos and reviews into this one request instead of four
        return restClient.get()
                .uri("/movie/{id}?language=en-US&append_to_response=credits,videos,reviews", tmdbId)
                .retrieve()
                .body(TmdbMovieDetails.class);
    }

    // Whole review texts for the AI digest. The stored audienceReviews are short snippets made for the page.
    // One page = up to 20 reviews
    public List<TmdbMovieDetails.ReviewEntry> reviews(int tmdbId) {
        TmdbMovieDetails.Reviews page = restClient.get()
                .uri("/movie/{id}/reviews?language=en-US&page=1", tmdbId)
                .retrieve()
                .body(TmdbMovieDetails.Reviews.class);
        return page == null || page.results() == null ? List.of() : page.results();
    }
}
