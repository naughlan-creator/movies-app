package dev.naughlan.movies.movie;

import java.time.Instant;
import java.util.List;

import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.DocumentReference;

import dev.naughlan.movies.review.Review;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Document(collection = "movies")
@Data // checks all fields of the Document are added
@AllArgsConstructor // ...like public Constructor(...) {}
@NoArgsConstructor // ...like public Constructor() {}
public class Movie {
    @Id
    private ObjectId id;
    private String imdbId;
    private String title;
    private String releaseDate;
    private String trailerLink;
    private String poster;
    private List<String> genres;
    private List<String> backdrops;
    @DocumentReference // only puts ids and leaves data in its class
    private List<Review> reviewIds;

    // Filled in from TMDB by TrendingMoviesSync
    private Integer tmdbId;
    private String overview;
    private Integer runtime; // minutes
    private Double rating; // TMDB audience score out of 10
    private List<CastMember> cast;
    private List<AudienceReview> audienceReviews;
    private Integer trendingRank; // 1 = most trending this week, null = not trending
    private Instant syncedAt;
}
