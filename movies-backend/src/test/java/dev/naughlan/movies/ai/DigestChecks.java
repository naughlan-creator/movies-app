package dev.naughlan.movies.ai;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** The graders that are pure code: free, instant, and never wrong about what they check. */
final class DigestChecks {

    record Result(boolean sentimentCorrect, List<String> forbiddenFound) {
        boolean forbiddenAbsent() {
            return forbiddenFound.isEmpty();
        }
    }

    private DigestChecks() {
    }

    static Result check(EvalCase evalCase, ReviewDigest digest) {
        String text = allText(digest);
        List<String> found = evalCase.expected().mustNotContain().stream()
                // Case-sensitive on purpose: "Rose" the character, not "tension rose"
                .filter(text::contains)
                .toList();
        return new Result(digest.sentiment() == evalCase.expected().sentiment(), found);
    }

    /** Every piece of text the digest would show on the page. */
    static String allText(ReviewDigest digest) {
        return Stream.of(Stream.of(digest.verdict(), digest.watchIf()), digest.liked().stream(), digest.disliked().stream())
                .flatMap(texts -> texts)
                .filter(Objects::nonNull)
                .collect(Collectors.joining("\n"));
    }
}