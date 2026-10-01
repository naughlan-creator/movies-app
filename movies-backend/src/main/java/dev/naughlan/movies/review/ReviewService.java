package dev.naughlan.movies.review;

import java.time.Instant;

import org.bson.types.ObjectId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.naughlan.movies.movie.MovieNotFoundException;
import dev.naughlan.movies.movie.MovieRepository;
import dev.naughlan.movies.outbox.Outbox;
import dev.naughlan.movies.security.CurrentUser;

@Service
public class ReviewService {
    // Newest first; _id breaks ties between reviews written in the same millisecond, so pages never overlap
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "createdAt", "id");

    private final ReviewRepository reviewRepository;
    private final MovieRepository movieRepository;
    private final Outbox outbox;

    public ReviewService(ReviewRepository reviewRepository, MovieRepository movieRepository, Outbox outbox) {
        this.reviewRepository = reviewRepository;
        this.movieRepository = movieRepository;
        this.outbox = outbox;
    }

    // The review and its event are written in one MongoDB transaction: both are saved, or neither is.
    // Calling Kafka here instead would reintroduce the two-write problem (a saved review with no event,
    // or an event for a review that was never saved). The OutboxRelay publishes the event afterwards.
    @Transactional
    public Review createReview(String reviewBody, String imdbId, CurrentUser author) {
        if (!movieRepository.existsByImdbId(imdbId)) {
            throw new MovieNotFoundException(imdbId);
        }
        Review review = reviewRepository.insert(
                new Review(reviewBody.trim(), imdbId, author.id(), author.username(), Instant.now()));
        outbox.record(ReviewEvents.TOPIC, imdbId, ReviewEvent.created(review));
        return review;
    }

    public Page<Review> reviewsForMovie(String imdbId, int page, int size) {
        if (!movieRepository.existsByImdbId(imdbId)) {
            throw new MovieNotFoundException(imdbId);
        }
        return reviewRepository.findByImdbId(imdbId, PageRequest.of(page, size, NEWEST_FIRST));
    }

    @Transactional
    public void deleteReview(String reviewId, CurrentUser user) {
        // A malformed id can't match anything, so it's a 404, not a 500
        if (!ObjectId.isValid(reviewId)) {
            throw new ReviewNotFoundException(reviewId);
        }
        Review review = reviewRepository.findById(new ObjectId(reviewId))
                .orElseThrow(() -> new ReviewNotFoundException(reviewId));

        // Object-level authorisation: load the object first, then decide.
        // Compare the stable id, not the username: usernames can change and be reused.
        if (!user.isAdmin() && !user.id().equals(review.getAuthorId())) {
            throw new AccessDeniedException("Only the review's author or an admin can delete it");
        }
        reviewRepository.delete(review);
        outbox.record(ReviewEvents.TOPIC, review.getImdbId(), ReviewEvent.deleted(review));
    }
}
