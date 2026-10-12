package dev.naughlan.movies.ai;

import java.util.List;
import java.util.stream.Stream;

/** One eval case from src/test/resources/eval/digest-cases.json. */
public record EvalCase(String id, List<String> tags, String title, String releaseDate,
                       List<String> tmdbReviews, List<String> appReviews, Expected expected) {

    public record Expected(ReviewDigest.Sentiment sentiment, List<String> mustNotContain, String why) {
    }

    List<String> allReviews() {
        return Stream.concat(tmdbReviews.stream(), appReviews.stream()).toList();
    }
}