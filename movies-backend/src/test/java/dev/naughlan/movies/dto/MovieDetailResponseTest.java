package dev.naughlan.movies.dto;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;

import dev.naughlan.movies.Movie;
import dev.naughlan.movies.Review;

class MovieDetailResponseTest {
    @Test
    void exposesReviewsWithStringIdsAndNeverReturnsNullLists() {
        ObjectId reviewId = new ObjectId();
        Movie movie = new Movie();
        movie.setImdbId("tt0000001");
        movie.setReviewIds(List.of(new Review(reviewId, "Great film")));
        // cast, genres, backdrops and audienceReviews are left null on purpose

        MovieDetailResponse response = MovieDetailResponse.from(movie);

        assertThat(response.reviews())
                .containsExactly(new ReviewResponse(reviewId.toHexString(), "Great film"));
        assertThat(response.cast()).isEmpty();
        assertThat(response.genres()).isEmpty();
        assertThat(response.backdrops()).isEmpty();
        assertThat(response.audienceReviews()).isEmpty();
    }
}
