package dev.naughlan.movies.review;

import java.time.Instant;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import dev.naughlan.movies.movie.Movie;
import dev.naughlan.movies.movie.MovieNotFoundException;
import dev.naughlan.movies.movie.MovieRepository;
import dev.naughlan.movies.security.CurrentUser;

@Service
public class ReviewService {
    private final ReviewRepository reviewRepository;
    private final MovieRepository movieRepository;
    private final MongoTemplate mongoTemplate;

    public ReviewService(ReviewRepository reviewRepository, MovieRepository movieRepository, MongoTemplate mongoTemplate) {
        this.reviewRepository = reviewRepository;
        this.movieRepository = movieRepository;
        this.mongoTemplate = mongoTemplate;
    }

    public Review createReview(String reviewBody, String imdbId, CurrentUser author) {
        // Check before inserting: the old code saved the review first, leaving orphans when the movie didn't exist
        if (!movieRepository.existsByImdbId(imdbId)) {
            throw new MovieNotFoundException(imdbId);
        }
        Review review = reviewRepository.insert(
                new Review(reviewBody.trim(), author.id(), author.username(), Instant.now()));

        mongoTemplate.update(Movie.class)
                .matching(Criteria.where("imdbId").is(imdbId))
                .apply(new Update().push("reviewIds").value(review))
                .first();

        return review;
    }

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

        // Unlink it from its movie first, then delete it. If we crash in between,
        // we're left with an unused review, not a movie pointing at a review that no longer exists.
        mongoTemplate.updateMulti(
                Query.query(Criteria.where("reviewIds").is(review.getId())),
                new Update().pull("reviewIds", review.getId()),
                Movie.class);
        reviewRepository.delete(review);
    }
}
