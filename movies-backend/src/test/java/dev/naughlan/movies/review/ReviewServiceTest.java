package dev.naughlan.movies.review;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.security.access.AccessDeniedException;

import dev.naughlan.movies.movie.MovieNotFoundException;
import dev.naughlan.movies.movie.MovieRepository;

class ReviewServiceTest {

    private final ReviewRepository reviewRepository = mock(ReviewRepository.class);
    private final MovieRepository movieRepository = mock(MovieRepository.class);
    private final MongoTemplate mongoTemplate = mock(MongoTemplate.class);
    private final ReviewService service = new ReviewService(reviewRepository, movieRepository, mongoTemplate);

    @Test
    void rejectsReviewForUnknownMovieWithoutSavingIt() {
        when(movieRepository.existsByImdbId("tt0000000")).thenReturn(false);

        assertThatThrownBy(() -> service.createReview("Great film", "tt0000000", "movie_fan_43"))
                .isInstanceOf(MovieNotFoundException.class);

        verify(reviewRepository, never()).insert(any(Review.class));
    }

    @Test
    void anotherUserCannotDeleteTheReview() {
        Review review = new Review(new ObjectId(), "Mine", "movie_fan_43", Instant.now());
        when(reviewRepository.findById(review.getId())).thenReturn(Optional.of(review));

        assertThatThrownBy(() -> service.deleteReview(review.getId().toHexString(), "movie_fan_42", false))
                .isInstanceOf(AccessDeniedException.class);
        verify(reviewRepository, never()).delete(any(Review.class));
    }

    @Test
    void adminCanDeleteAnyonesReview() {
        Review review = new Review(new ObjectId(), "Spam", "movie_fan_42", Instant.now());
        when(reviewRepository.findById(review.getId())).thenReturn(Optional.of(review));

        service.deleteReview(review.getId().toHexString(), "movie_fan_43", true);

        verify(reviewRepository).delete(review);
    }

    @Test
    void malformedIdIsNotFound() {
        assertThatThrownBy(() -> service.deleteReview("not-an-object-id", "movie_fan_43", false))
                .isInstanceOf(ReviewNotFoundException.class);
    }
}