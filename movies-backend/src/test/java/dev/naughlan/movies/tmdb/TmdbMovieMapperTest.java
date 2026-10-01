package dev.naughlan.movies.tmdb;

import dev.naughlan.movies.movie.AudienceReview;
import dev.naughlan.movies.movie.Movie;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TmdbMovieMapperTest {

    private final TmdbMovieMapper mapper =
            new TmdbMovieMapper(new TmdbProperties(null, "https://image.tmdb.org/t/p", null, 10));

    // A trimmed-down copy of a real TMDB /movie/{id} response
    private static final String DETAILS_JSON = """
            {
              "id": 550,
              "imdb_id": "tt0137523",
              "title": "Fight Club",
              "overview": "An insomniac office worker forms an underground fight club.",
              "release_date": "1999-10-15",
              "runtime": 139,
              "vote_average": 8.4,
              "poster_path": "/poster.jpg",
              "backdrop_path": "/backdrop.jpg",
              "genres": [{"id": 18, "name": "Drama"}],
              "some_field_we_ignore": true,
              "credits": {"cast": [
                {"name": "Helena Bonham Carter", "character": "Marla Singer", "profile_path": "/hbc.jpg", "order": 2},
                {"name": "Edward Norton", "character": "The Narrator", "profile_path": "/en.jpg", "order": 0},
                {"name": "Meat Loaf", "character": "Robert Paulson", "profile_path": null, "order": 3},
                {"name": "Brad Pitt", "character": "Tyler Durden", "profile_path": "/bp.jpg", "order": 1}
              ]},
              "videos": {"results": [
                {"key": "teaser00001", "site": "YouTube", "type": "Teaser", "official": true},
                {"key": "fanTrailer1", "site": "YouTube", "type": "Trailer", "official": false},
                {"key": "officialTr1", "site": "YouTube", "type": "Trailer", "official": true}
              ]},
              "reviews": {"results": [
                {"author": "unrated", "author_details": {"rating": null}, "content": "No score given.", "created_at": "2024-01-01T00:00:00.000Z", "url": "https://tmdb/r/1"},
                {"author": "fan", "author_details": {"rating": 10.0}, "content": "Masterpiece.", "created_at": "2023-01-01T00:00:00.000Z", "url": "https://tmdb/r/2"},
                {"author": "critic", "author_details": {"rating": 6.0}, "content": "Overrated.", "created_at": "2023-06-01T00:00:00.000Z", "url": "https://tmdb/r/3"},
                {"author": "newer-fan", "author_details": {"rating": 10.0}, "content": "Still great.", "created_at": "2024-06-01T00:00:00.000Z", "url": "https://tmdb/r/4"}
              ]}
            }
            """;

    private Movie mapSample() {
        TmdbMovieDetails details = JsonMapper.builder().build().readValue(DETAILS_JSON, TmdbMovieDetails.class);
        return mapper.toMovie(details, 1, Instant.parse("2026-09-23T00:00:00Z"));
    }

    @Test
    void mapsSnakeCaseFieldsAndBuildsImageUrls() {
        Movie movie = mapSample();

        assertThat(movie.getImdbId()).isEqualTo("tt0137523");
        assertThat(movie.getReleaseDate()).isEqualTo("1999-10-15");
        assertThat(movie.getRating()).isEqualTo(8.4);
        assertThat(movie.getRuntime()).isEqualTo(139);
        assertThat(movie.getPoster()).isEqualTo("https://image.tmdb.org/t/p/w500/poster.jpg");
        assertThat(movie.getBackdrops()).containsExactly("https://image.tmdb.org/t/p/w1280/backdrop.jpg");
        assertThat(movie.getGenres()).containsExactly("Drama");
        assertThat(movie.getTrendingRank()).isEqualTo(1);
    }

    @Test
    void picksTheOfficialYouTubeTrailer() {
        assertThat(mapSample().getTrailerLink()).isEqualTo("https://www.youtube.com/watch?v=officialTr1");
    }

    @Test
    void keepsTopThreeCastInBillingOrder() {
        Movie movie = mapSample();

        assertThat(movie.getCast()).extracting("name")
                .containsExactly("Edward Norton", "Brad Pitt", "Helena Bonham Carter");
        assertThat(movie.getCast().get(0).profileUrl()).isEqualTo("https://image.tmdb.org/t/p/w185/en.jpg");
    }

    @Test
    void keepsThreeHighestRatedReviewsNewestFirstOnTies() {
        List<AudienceReview> reviews = mapSample().getAudienceReviews();

        assertThat(reviews).extracting(AudienceReview::author)
                .containsExactly("newer-fan", "fan", "critic");
    }

    @Test
    void handlesMissingOptionalSections() {
        TmdbMovieDetails bare = new TmdbMovieDetails(1, "tt1", "Bare", null, null, null, null,
                null, null, null, null, null, null);

        Movie movie = mapper.toMovie(bare, 5, Instant.now());

        assertThat(movie.getPoster()).isNull();
        assertThat(movie.getBackdrops()).isEmpty();
        assertThat(movie.getTrailerLink()).isNull();
        assertThat(movie.getCast()).isEmpty();
        assertThat(movie.getAudienceReviews()).isEmpty();
    }

    @Test
    void cutsLongReviewsAtAWordBoundary() {
        String longReview = "word ".repeat(200); // 1000 characters

        String snippet = TmdbMovieMapper.snippet(longReview);

        assertThat(snippet).hasSizeLessThanOrEqualTo(TmdbMovieMapper.REVIEW_SNIPPET_LENGTH + 1).endsWith("word…");
        assertThat(TmdbMovieMapper.snippet("Short  and\r\n\r\nsweet")).isEqualTo("Short and sweet");
    }
}
