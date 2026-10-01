package dev.naughlan.movies.review;

import org.bson.types.ObjectId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ReviewRepository extends MongoRepository<Review, ObjectId> {

    // Served by the (imdbId, createdAt, _id) index created in ReviewIndexes
    Page<Review> findByImdbId(String imdbId, Pageable pageable);

    long countByImdbId(String imdbId);
}
