package dev.naughlan.movies.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * What the model must return. The SDK turns this record into a JSON Schema,
 * and each description becomes part of the instructions for that field.
 */
public record ReviewDigest(
        @JsonPropertyDescription("2 to 3 sentences: the overall verdict and the main reasons for it") String verdict,
        @JsonPropertyDescription("Up to 3 things viewers liked, each a short phrase. Empty if none.") List<String> liked,
        @JsonPropertyDescription("Up to 3 things viewers disliked, each a short phrase. Empty if none.") List<String> disliked,
        @JsonPropertyDescription("One sentence: who will enjoy this movie. Empty if the reviews don't say.") String watchIf,
        @JsonPropertyDescription("Overall mood of the reviews. UNKNOWN when there are too few reviews to judge.") Sentiment sentiment) {

    public enum Sentiment {
        POSITIVE, MIXED, NEGATIVE, UNKNOWN
    }

    static ReviewDigest noReviews() {
        return new ReviewDigest("No reviews yet.", List.of(), List.of(), "", Sentiment.UNKNOWN);
    }

    // A link or email in a digest means something went wrong (or someone injected
    // it): never publish it
    private static final Pattern LINK_OR_EMAIL = Pattern.compile(
            "(?i)https?://|www\\.|[\\w.+-]+@[\\w-]+\\.|\\b[a-z0-9-]+\\.(com|net|org|io|xyz|info|biz|ru|top)\\b");
    private static final int MAX_FIELD_LENGTH = 600;
    private static final int MAX_ITEMS = 3;

    /** Reasons this digest is unsafe to show; empty when it's fine. */
    List<String> problems() {
        List<String> problems = new ArrayList<>();
        if (verdict == null || verdict.isBlank()) {
            problems.add("empty verdict");
        }
        if (liked.size() > MAX_ITEMS || disliked.size() > MAX_ITEMS) {
            problems.add("too many items");
        }
        if (allText().anyMatch(text -> text.length() > MAX_FIELD_LENGTH)) {
            problems.add("text too long");
        }
        if (allText().anyMatch(text -> LINK_OR_EMAIL.matcher(text).find())) {
            problems.add("contains a link or email address");
        }
        return problems;
    }

    private Stream<String> allText() {
        return Stream.of(Stream.of(verdict, watchIf), liked.stream(), disliked.stream())
                .flatMap(texts -> texts)
                .filter(Objects::nonNull);
    }
}