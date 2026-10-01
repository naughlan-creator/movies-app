package dev.naughlan.movies.review;

import java.time.Instant;

import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Document(collection = "reviews")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Review {
    @Id
    private ObjectId id;
    private String body;
    // The movie this review is about. The review points at the movie; the movie doesn't list its reviews.
    private String imdbId;
    // The author's stable Keycloak id ("sub"). Ownership checks use this, never the name.
    // Null for reviews written before Keycloak: only admins can delete those.
    private String authorId;
    // Display name at the time of writing. Stored in the existing "author" field,
    // so reviews written before this change keep showing their author's name.
    @Field("author")
    private String authorName;
    private Instant createdAt;

    public Review(String body, String imdbId, String authorId, String authorName, Instant createdAt) {
        this.body = body;
        this.imdbId = imdbId;
        this.authorId = authorId;
        this.authorName = authorName;
        this.createdAt = createdAt;
    }
}
