package dev.naughlan.movies.user;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Set;

@Document(collection = "users")
public record User(
        @Id String id,
        String username,
        String passwordHash,
        Set<Role> roles,
        Instant createdAt) {
}