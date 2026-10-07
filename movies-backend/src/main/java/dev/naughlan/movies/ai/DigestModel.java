package dev.naughlan.movies.ai;

/**
 * The one thing in the digest feature that talks to an LLM. Everything else depends on this interface,
 * so tests (and later, evals or another provider) can swap the implementation.
 */
public interface DigestModel {

    Result summarize(String systemPrompt, String userPrompt);

    record Result(ReviewDigest digest, String model, String stopReason,
                  long inputTokens, long outputTokens, long latencyMs) {
    }
}