package dev.naughlan.movies.movie;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;

import dev.naughlan.movies.review.Review;
import dev.naughlan.movies.review.ReviewResponse;

class MovieDetailResponseTest {
    @Test
    void exposesReviewsWithStringIdsAndNeverReturnsNullLists() {
        ObjectId reviewId = new ObjectId();
        Movie movie = new Movie();
        movie.setImdbId("tt0000001");
        Instant written = Instant.parse("2026-09-30T10:00:00Z");
        movie.setReviewIds(List.of(new Review(reviewId, "Great film", "movie_fan_43", written)));
        // cast, genres, backdrops and audienceReviews are left null on purpose

        MovieDetailResponse response = MovieDetailResponse.from(movie);

        assertThat(response.reviews())
                .containsExactly(new ReviewResponse(reviewId.toHexString(), "Great film", "movie_fan_43", written));
        assertThat(response.cast()).isEmpty();
        assertThat(response.genres()).isEmpty();
        assertThat(response.backdrops()).isEmpty();
        assertThat(response.audienceReviews()).isEmpty();
    }
}
