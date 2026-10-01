package dev.naughlan.movies.tmdb;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

import dev.naughlan.movies.movie.Movie;

/**
 * On every startup, replaces the "trending this week" list with fresh data from TMDB.
 * Only TMDB fields are written; reviews live in their own collection and are never touched.
 */
@Component
public class TrendingMoviesSync {

    private static final Logger log = LoggerFactory.getLogger(TrendingMoviesSync.class);

    private final TmdbProperties properties;
    private final TmdbClient tmdbClient;
    private final TmdbMovieMapper mapper;
    private final MongoTemplate mongoTemplate;

    public TrendingMoviesSync(TmdbProperties properties, TmdbClient tmdbClient,
                              TmdbMovieMapper mapper, MongoTemplate mongoTemplate) {
        this.properties = properties;
        this.tmdbClient = tmdbClient;
        this.mapper = mapper;
        this.mongoTemplate = mongoTemplate;
    }

    // ApplicationReadyEvent fires once the web server is up, so a slow TMDB doesn't delay startup
    @EventListener(ApplicationReadyEvent.class)
    public void syncOnStartup() {
        if (!properties.hasApiToken()) {
            log.warn("TMDB_API_TOKEN is not set; skipping trending sync and serving movies already in the database");
            return;
        }
        try {
            int count = syncTrendingMovies();
            log.info("Synced {} trending movies from TMDB", count);
        } catch (RestClientException e) {
            // A TMDB outage must not take our app down; the previous list keeps being served
            log.warn("Trending sync failed, keeping the previous list: {}", e.getMessage());
        }
    }

    int syncTrendingMovies() {
        Instant now = Instant.now();

        // 1. Fetch everything first, so a failure half way leaves the database untouched
        List<Movie> trending = new ArrayList<>();
        for (TmdbTrendingPage.Result result : tmdbClient.trendingThisWeek()) {
            if (trending.size() >= properties.trendingLimit()) {
                break;
            }
            TmdbMovieDetails details = tmdbClient.movieDetails(result.id());
            // Our URLs and reviews are keyed by IMDb id; very new releases sometimes don't have one yet
            if (details == null || details.imdbId() == null || details.imdbId().isBlank()) {
                log.debug("Skipping '{}': no IMDb id yet", result.title());
                continue;
            }
            trending.add(mapper.toMovie(details, trending.size() + 1, now));
        }
        if (trending.isEmpty()) {
            return 0;
        }

        // 2. Last week's movies stay in the database (with their reviews) but drop off the trending list
        mongoTemplate.updateMulti(
                Query.query(Criteria.where("trendingRank").exists(true)),
                new Update().unset("trendingRank"),
                Movie.class);

        // 3. Upsert = update the movie if its imdbId exists, insert it otherwise.
        //    Only TMDB fields are set, so anything else stored on the movie survives.
        for (Movie movie : trending) {
            mongoTemplate.upsert(
                    Query.query(Criteria.where("imdbId").is(movie.getImdbId())),
                    tmdbFields(movie),
                    Movie.class);
        }
        return trending.size();
    }

    private Update tmdbFields(Movie movie) {
        return new Update()
                .set("tmdbId", movie.getTmdbId())
                .set("title", movie.getTitle())
                .set("releaseDate", movie.getReleaseDate())
                .set("overview", movie.getOverview())
                .set("runtime", movie.getRuntime())
                .set("rating", movie.getRating())
                .set("poster", movie.getPoster())
                .set("backdrops", movie.getBackdrops())
                .set("genres", movie.getGenres())
                .set("trailerLink", movie.getTrailerLink())
                .set("cast", movie.getCast())
                .set("audienceReviews", movie.getAudienceReviews())
                .set("trendingRank", movie.getTrendingRank())
                .set("syncedAt", movie.getSyncedAt());
    }
}
