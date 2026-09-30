package dev.naughlan.movies.review;

import java.time.Instant;

import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

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
    private String author;      // username; null for reviews written before accounts existed
    private Instant createdAt;

    public Review(String body, String author, Instant createdAt) {
        this.body = body;
        this.author = author;
        this.createdAt = createdAt;
    }
}