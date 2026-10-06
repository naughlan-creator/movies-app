package dev.naughlan.movies.ai;

import java.util.List;

public class UnsafeDigestException extends RuntimeException {

    public UnsafeDigestException(String imdbId, List<String> problems) {
        super("Digest for " + imdbId + " failed safety checks: " + problems);
    }
}