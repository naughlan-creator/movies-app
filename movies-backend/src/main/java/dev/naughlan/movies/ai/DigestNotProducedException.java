package dev.naughlan.movies.ai;

/** The model answered, but not with a usable digest: it refused, or ran out of tokens mid-answer. */
public class DigestNotProducedException extends RuntimeException {

    public DigestNotProducedException(String reason) {
        super("No digest produced: " + reason);
    }
}