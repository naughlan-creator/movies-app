package dev.naughlan.movies;

import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReviewServiceTest {

    private final ReviewRepository reviewRepository = mock(ReviewRepository.class);
    private final MovieRepository movieRepository = mock(MovieRepository.class);
    private final MongoTemplate mongoTemplate = mock(MongoTemplate.class);
    private final ReviewService service = new ReviewService(reviewRepository, movieRepository, mongoTemplate);

    @Test
    void rejectsReviewForUnknownMovieWithoutSavingIt() {
        when(movieRepository.existsByImdbId("tt0000000")).thenReturn(false);

        assertThatThrownBy(() -> service.createReview("Great film", "tt0000000"))
                .isInstanceOf(MovieNotFoundException.class);

        verify(reviewRepository, never()).insert(any(Review.class));
    }
}