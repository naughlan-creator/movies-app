package dev.naughlan.movies.ai;

import org.springframework.data.mongodb.repository.MongoRepository;

interface StoredDigestRepository extends MongoRepository<StoredDigest, String> {
}