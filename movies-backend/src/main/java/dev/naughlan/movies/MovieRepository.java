package dev.naughlan.movies;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MovieRepository extends MongoRepository<Movie, ObjectId> {
    Optional<Movie> findMovieByImdbId(String imdbId);

    // Spring Data builds the query from the method name: { trendingRank: { $ne: null } } sorted by trendingRank
    List<Movie> findByTrendingRankNotNullOrderByTrendingRankAsc();
}
