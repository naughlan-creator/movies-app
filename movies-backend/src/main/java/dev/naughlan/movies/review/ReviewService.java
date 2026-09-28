package dev.naughlan.movies.review;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import dev.naughlan.movies.movie.Movie;
import dev.naughlan.movies.movie.MovieNotFoundException;
import dev.naughlan.movies.movie.MovieRepository;

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

    public Review createReview(String reviewBody, String imdbId) {
        // Check before inserting: the old code saved the review first, leaving orphans when the movie didn't exist
        if (!movieRepository.existsByImdbId(imdbId)) {
            throw new MovieNotFoundException(imdbId);
        }
        Review review = reviewRepository.insert(new Review(reviewBody.trim()));

        mongoTemplate.update(Movie.class)
                .matching(Criteria.where("imdbId").is(imdbId))
                .apply(new Update().push("reviewIds").value(review))
                .first();

        return review;
    }
}
