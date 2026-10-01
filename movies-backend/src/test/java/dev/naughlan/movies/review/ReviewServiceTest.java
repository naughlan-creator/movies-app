package dev.naughlan.movies.review;

import static dev.naughlan.movies.TestUsers.MOVIE_FAN_42;
import static dev.naughlan.movies.TestUsers.MOVIE_FAN_43;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import dev.naughlan.movies.movie.MovieNotFoundException;
import dev.naughlan.movies.movie.MovieRepository;
import dev.naughlan.movies.outbox.Outbox;
import dev.naughlan.movies.security.CurrentUser;

class ReviewServiceTest {

    private final ReviewRepository reviewRepository = mock(ReviewRepository.class);
    private final MovieRepository movieRepository = mock(MovieRepository.class);
    private final Outbox outbox = mock(Outbox.class);
    private final ReviewService service = new ReviewService(reviewRepository, movieRepository, outbox);

    private static Review reviewBy(CurrentUser author) {
        return new Review(new ObjectId(), "Some opinion", "tt0000001", author.id(), author.username(), Instant.now());
    }

    @Test
    void rejectsReviewForUnknownMovieWithoutSavingIt() {
        when(movieRepository.existsByImdbId("tt0000000")).thenReturn(false);

        assertThatThrownBy(() -> service.createReview("Great film", "tt0000000", MOVIE_FAN_43))
                .isInstanceOf(MovieNotFoundException.class);

        verify(reviewRepository, never()).insert(any(Review.class));
        verify(outbox, never()).record(any(), any(), any());
    }

    @Test
    void creatingAReviewRecordsACreatedEventKeyedByMovie() {
        when(movieRepository.existsByImdbId("tt0000001")).thenReturn(true);
        when(reviewRepository.insert(any(Review.class))).thenAnswer(call -> {
            Review saved = call.getArgument(0);
            saved.setId(new ObjectId());
            return saved;
        });

        service.createReview("  Great film  ", "tt0000001", MOVIE_FAN_43);

        verify(outbox).record(eq(ReviewEvents.TOPIC), eq("tt0000001"), argThat(event ->
                event instanceof ReviewEvent e
                        && e.type() == ReviewEvent.Type.CREATED
                        && e.body().equals("Great film")
                        && e.authorId().equals(MOVIE_FAN_43.id())));
    }

    @Test
    void anotherUserCannotDeleteTheReview() {
        Review review = reviewBy(MOVIE_FAN_43);
        when(reviewRepository.findById(review.getId())).thenReturn(Optional.of(review));

        assertThatThrownBy(() -> service.deleteReview(review.getId().toHexString(), MOVIE_FAN_42))
                .isInstanceOf(AccessDeniedException.class);
        verify(reviewRepository, never()).delete(any(Review.class));
        verify(outbox, never()).record(any(), any(), any());
    }

    @Test
    void ownershipFollowsTheStableIdNotTheUsername() {
        // movie_fan_42 renamed their account and someone new registered "movie_fan_42".
        // Same username, different person: they must not own the old reviews.
        Review review = reviewBy(MOVIE_FAN_42);
        when(reviewRepository.findById(review.getId())).thenReturn(Optional.of(review));
        CurrentUser newcomerWithReusedName = new CurrentUser("a-different-keycloak-id", "movie_fan_42", Set.of("USER"));

        assertThatThrownBy(() -> service.deleteReview(review.getId().toHexString(), newcomerWithReusedName))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void adminCanDeleteAnyonesReview() {
        Review review = reviewBy(MOVIE_FAN_42);
        when(reviewRepository.findById(review.getId())).thenReturn(Optional.of(review));

        service.deleteReview(review.getId().toHexString(), MOVIE_FAN_43);

        verify(reviewRepository).delete(review);
        verify(outbox).record(eq(ReviewEvents.TOPIC), eq("tt0000001"),
                argThat(event -> event instanceof ReviewEvent e && e.type() == ReviewEvent.Type.DELETED));
    }

    @Test
    void legacyReviewsWithoutAnAuthorIdCanOnlyBeDeletedByAdmins() {
        Review legacy = new Review(new ObjectId(), "Written before accounts existed", "tt0000001", null, "movie_fan", null);
        when(reviewRepository.findById(legacy.getId())).thenReturn(Optional.of(legacy));

        assertThatThrownBy(() -> service.deleteReview(legacy.getId().toHexString(), MOVIE_FAN_42))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void malformedIdIsNotFound() {
        assertThatThrownBy(() -> service.deleteReview("not-an-object-id", MOVIE_FAN_43))
                .isInstanceOf(ReviewNotFoundException.class);
    }
}
