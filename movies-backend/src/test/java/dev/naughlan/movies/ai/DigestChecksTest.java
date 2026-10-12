package dev.naughlan.movies.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.List;

import org.junit.jupiter.api.Test;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

class DigestChecksTest {

    private static final EvalCase TITANIC = new EvalCase("t", List.of("grounding"), "Titanic", "1997-12-19",
            List.of("A sincere epic."), List.of(),
            new EvalCase.Expected(ReviewDigest.Sentiment.POSITIVE, List.of("DiCaprio", "Rose"), "test"));

    private static ReviewDigest digest(String verdict, ReviewDigest.Sentiment sentiment) {
        return new ReviewDigest(verdict, List.of("The sinking"), List.of("Cheesy dialogue"), "", sentiment);
    }

    // The "oracle": a perfect answer must pass every check
    @Test
    void aCorrectCleanDigestPasses() {
        DigestChecks.Result result = DigestChecks.check(TITANIC, digest("A sincere epic.", ReviewDigest.Sentiment.POSITIVE));

        assertThat(result.sentimentCorrect()).isTrue();
        assertThat(result.forbiddenAbsent()).isTrue();
    }

    @Test
    void aWrongSentimentFails() {
        assertThat(DigestChecks.check(TITANIC, digest("A sincere epic.", ReviewDigest.Sentiment.MIXED)).sentimentCorrect())
                .isFalse();
    }

    @Test
    void forbiddenTextIsCaughtCaseSensitively() {
        assertThat(DigestChecks.check(TITANIC, digest("DiCaprio shines.", ReviewDigest.Sentiment.POSITIVE)).forbiddenFound())
                .containsExactly("DiCaprio");
        // "rose" the verb is not "Rose" the character
        assertThat(DigestChecks.check(TITANIC, digest("Tension rose steadily.", ReviewDigest.Sentiment.POSITIVE)).forbiddenAbsent())
                .isTrue();
    }

    // The case file is data, and data has bugs too: check it obeys our own labeling rules
    @Test
    void theCaseFileIsWellFormed() throws Exception {
        List<EvalCase> cases;
        try (InputStream json = getClass().getResourceAsStream("/eval/digest-cases.json")) {
            cases = JsonMapper.builder().build().readValue(json, new TypeReference<List<EvalCase>>() { });
        }

        assertThat(cases).hasSize(25);
        assertThat(cases).extracting(EvalCase::id).doesNotHaveDuplicates();
        assertThat(cases).allSatisfy(c -> {
            assertThat(c.tags()).isNotEmpty();
            assertThat(c.expected().sentiment()).isNotNull();
            // Labeling rule: UNKNOWN exactly when there are fewer than 3 reviews
            assertThat(c.expected().sentiment() == ReviewDigest.Sentiment.UNKNOWN)
                    .as("UNKNOWN label vs review count for %s", c.id())
                    .isEqualTo(c.allReviews().size() < 3);
        });
    }
}