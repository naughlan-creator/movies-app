package dev.naughlan.movies.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import com.anthropic.client.AnthropicClient;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;

import dev.naughlan.movies.TestcontainersConfiguration;
import dev.naughlan.movies.ai.ReviewDigestService.DigestInput;
import dev.naughlan.movies.ai.ReviewDigestService.DigestPreview;
import dev.naughlan.movies.movie.AudienceReview;
import dev.naughlan.movies.movie.Movie;
import dev.naughlan.movies.movie.MovieRepository;
import dev.naughlan.movies.review.Review;
import dev.naughlan.movies.review.ReviewRepository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The review-digest eval. Calls the REAL model (costs money), so it never runs
 * in a normal build:
 * .\mvnw test -Dtest=ReviewDigestEval#run -Deval=true all cases
 * .\mvnw test -Dtest=ReviewDigestEval#run -Deval=true -Deval.cases=a,b only
 * these cases (a pilot)
 * -Deval.variant=v1 -Deval.reps=2 a new variant, repeated runs
 * Writes evals/review-digest/<variant>/results.jsonl, traces/ and errors.jsonl.
 * Re-running resumes:
 * finished (case, rep) pairs are skipped, so a crash never costs the work
 * already paid for.
 */
@Tag("eval")
@EnabledIfSystemProperty(named = "eval", matches = "true")
@SpringBootTest(properties = {
        // The test profile blanks the key on purpose; the eval needs the real one from
        // .env
        "app.ai.api-key=${ANTHROPIC_API_KEY:}",
        "app.ai.digest.refresh-enabled=false"
})
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class ReviewDigestEval {

    private static final Path FLOW = Path.of(System.getProperty("eval.out", "../evals/review-digest"));
    private static final String VARIANT = System.getProperty("eval.variant", "baseline");
    private static final int REPS = Integer.getInteger("eval.reps", 1);
    private static final Set<String> ONLY = Arrays.stream(System.getProperty("eval.cases", "").split(","))
            .map(String::trim).filter(id -> !id.isEmpty()).collect(Collectors.toSet());

    @Autowired
    private ReviewDigestService digestService;

    @Autowired
    private MovieRepository movieRepository;

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private AnthropicClient client;

    @Autowired
    private AiProperties ai;

    @Autowired
    private JsonMapper jsonMapper;

    @Test
    void run() throws IOException {
        assertThat(ai.hasApiKey()).as("ANTHROPIC_API_KEY must be set in movies-backend/.env").isTrue();

        Path dir = FLOW.resolve(VARIANT);
        Files.createDirectories(dir.resolve("traces"));
        Path results = dir.resolve("results.jsonl");
        Set<String> done = alreadyScored(results);
        GroundingJudge judge = new GroundingJudge(client);

        movieRepository.deleteAll();
        reviewRepository.deleteAll();

        for (EvalCase evalCase : loadCases()) {
            if (!ONLY.isEmpty() && !ONLY.contains(evalCase.id())) {
                continue;
            }
            String imdbId = seed(evalCase);
            for (int rep = 0; rep < REPS; rep++) {
                if (done.contains(evalCase.id() + "#" + rep)) {
                    continue; // already paid for and scored
                }
                try {
                    append(results, runCase(evalCase, imdbId, rep, judge, dir));
                } catch (RuntimeException e) {
                    // A broken run, not a bad answer: keep it out of results.jsonl so a re-run
                    // retries it
                    append(dir.resolve("errors.jsonl"), Map.of(
                            "prompt_id", evalCase.id(), "rep", rep, "failure", failureClass(e), "error", e.toString()));
                    System.out.printf("ERROR %s rep %d: %s%n", evalCase.id(), rep, e);
                }
            }
        }
        printSummary(results);
    }

    // Before trusting the judge, make sure it fails obviously bad digests. Three
    // paid judge calls.
    @Test
    void judgeFailsObviouslyBadDigests() throws IOException {
        EvalCase godfather = loadCases().stream()
                .filter(c -> c.id().equals("grounding-01")).findFirst().orElseThrow();
        GroundingJudge judge = new GroundingJudge(client);

        ReviewDigest empty = new ReviewDigest("", List.of(), List.of(), "", ReviewDigest.Sentiment.POSITIVE);
        ReviewDigest dontKnow = new ReviewDigest("I don't know.", List.of(), List.of(), "",
                ReviewDigest.Sentiment.UNKNOWN);
        ReviewDigest wrongMovie = new ReviewDigest(
                "A dazzling space adventure in which rebels fight a galactic empire with laser swords.",
                List.of("The space battles"), List.of("The comic relief robots"), "Fans of science fiction",
                ReviewDigest.Sentiment.POSITIVE);

        // Saying nothing is "grounded" (no false claims) but covers nothing: each
        // metric checks one thing
        assertThat(judge.grade(godfather, empty).verdict().coversMainPoints()).isFalse();
        assertThat(judge.grade(godfather, dontKnow).verdict().coversMainPoints()).isFalse();
        GroundingJudge.Verdict wrong = judge.grade(godfather, wrongMovie).verdict();
        assertThat(wrong.grounded()).isFalse();
        assertThat(wrong.coversMainPoints()).isFalse();
    }

    private Map<String, Object> runCase(EvalCase evalCase, String imdbId, int rep, GroundingJudge judge, Path dir)
            throws IOException {
        // The exact prompt the service builds, for the transcript
        DigestInput input = digestService.collectInput(imdbId);

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("prompt_id", evalCase.id());
        row.put("rep", rep);
        row.put("tags", evalCase.tags());
        row.put("prompt", input.prompt());

        List<Map<String, Object>> trace = new ArrayList<>();
        trace.add(Map.of("role", "system", "content", ReviewDigestService.SYSTEM_PROMPT));
        trace.add(Map.of("role", "user", "content", input.prompt()));

        DigestPreview preview;
        try {
            // The real code path: input selection, prompt, Claude, stop-reason and safety
            // checks
            preview = digestService.preview(imdbId);
        } catch (UnsafeDigestException | DigestNotProducedException e) {
            // The model answered but nothing usable came out: that's a graded failure, not
            // a harness error
            row.put("status", e.getMessage().contains("max_tokens") ? "truncated" : "ok");
            row.put("grade", grade(false, false, false, false, false));
            row.put("meta", Map.of("expected", evalCase.expected().sentiment().name(), "failure", e.getMessage()));
            trace.add(Map.of("role", "assistant", "content", "(no digest) " + e.getMessage()));
            writeTrace(dir, evalCase.id(), rep, trace);
            System.out.printf("%-14s REJECTED  %s%n", evalCase.id(), e.getMessage());
            return row;
        }

        ReviewDigest digest = preview.digest();
        trace.add(Map.of("role", "assistant", "content",
                jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(digest)));
        writeTrace(dir, evalCase.id(), rep, trace);

        DigestChecks.Result checks = DigestChecks.check(evalCase, digest);
        GroundingJudge.Graded judged = judge.grade(evalCase, digest);
        GroundingJudge.Verdict verdict = judged.verdict();

        row.put("status", "ok");
        row.put("grade", grade(checks.sentimentCorrect(), verdict.grounded(), checks.forbiddenAbsent(),
                verdict.coversMainPoints(), true));
        row.put("explanation", Map.of(
                "sentiment", "expected " + evalCase.expected().sentiment() + ", got " + digest.sentiment(),
                "grounded", verdict.grounded() ? verdict.explanation()
                        : "Unsupported: " + verdict.unsupportedClaims() + ". " + verdict.explanation(),
                "no_forbidden", checks.forbiddenAbsent() ? "none found" : "found " + checks.forbiddenFound(),
                "coverage", verdict.explanation()));
        // model comes from the response, not config: a fallback model shows up here
        row.put("model", preview.model());
        row.put("usage", Map.of("input_tokens", preview.inputTokens(), "output_tokens", preview.outputTokens()));
        row.put("judge_model", judged.model());
        row.put("judge_usage", Map.of("input_tokens", judged.inputTokens(), "output_tokens", judged.outputTokens()));
        row.put("latency_s", preview.latencyMs() / 1000.0);
        row.put("meta", Map.of("expected", evalCase.expected().sentiment().name(), "why", evalCase.expected().why()));

        System.out.printf("%-14s %-8s grounded=%-5s forbidden=%s%n", evalCase.id(), digest.sentiment(),
                verdict.grounded(), checks.forbiddenFound());
        return row;
    }

    // Order matters: the first metric is the report's headline
    private static Map<String, Object> grade(boolean sentiment, boolean grounded, boolean noForbidden,
            boolean coverage, boolean safe) {
        Map<String, Object> grade = new LinkedHashMap<>();
        grade.put("sentiment", sentiment ? 1 : 0);
        grade.put("grounded", grounded ? 1 : 0);
        grade.put("no_forbidden", noForbidden ? 1 : 0);
        grade.put("coverage", coverage ? 1 : 0);
        grade.put("safe", safe ? 1 : 0);
        return grade;
    }

    // Each case becomes a movie with stored TMDB reviews (tmdbId stays null, so no
    // live TMDB call) plus app reviews
    private String seed(EvalCase evalCase) {
        String imdbId = "eval-" + evalCase.id();
        Movie movie = new Movie();
        movie.setImdbId(imdbId);
        movie.setTitle(evalCase.title());
        movie.setReleaseDate(evalCase.releaseDate());
        movie.setAudienceReviews(evalCase.tmdbReviews().stream()
                .map(text -> new AudienceReview("tmdb-user", null, text, null, null))
                .toList());
        movieRepository.save(movie);

        // Newest first is how the service reads them, so count createdAt down to keep
        // the file's order
        Instant now = Instant.now();
        for (int i = 0; i < evalCase.appReviews().size(); i++) {
            reviewRepository.save(new Review(evalCase.appReviews().get(i), imdbId, "eval-user-" + i,
                    "Eval User " + i, now.minusSeconds(i)));
        }
        return imdbId;
    }

    private List<EvalCase> loadCases() throws IOException {
        try (InputStream json = getClass().getResourceAsStream("/eval/digest-cases.json")) {
            return jsonMapper.readValue(json, new TypeReference<List<EvalCase>>() {
            });
        }
    }

    private Set<String> alreadyScored(Path results) throws IOException {
        Set<String> done = new HashSet<>();
        if (Files.exists(results)) {
            for (String line : Files.readAllLines(results)) {
                JsonNode row = jsonMapper.readTree(line);
                done.add(row.get("prompt_id").asString() + "#" + row.get("rep").asInt());
            }
        }
        return done;
    }

    // Written as each case finishes, so a crash halfway keeps everything before it
    private void append(Path file, Map<String, Object> row) throws IOException {
        Files.writeString(file, jsonMapper.writeValueAsString(row) + System.lineSeparator(),
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    private void writeTrace(Path dir, String id, int rep, List<Map<String, Object>> trace) throws IOException {
        Files.writeString(dir.resolve("traces").resolve(id + "_rep" + rep + ".json"),
                jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(trace));
    }

    private static String failureClass(RuntimeException e) {
        if (e instanceof AnthropicIoException) {
            return "timeout_or_network";
        }
        if (e instanceof AnthropicServiceException) {
            return "api_error";
        }
        return "harness_error";
    }

    // Each metric's pass rate with a 95% margin: with 25 cases, differences smaller
    // than the margin are noise
    private void printSummary(Path results) throws IOException {
        List<JsonNode> rows = new ArrayList<>();
        for (String line : Files.readAllLines(results)) {
            JsonNode row = jsonMapper.readTree(line);
            if ("ok".equals(row.get("status").asString())) {
                rows.add(row);
            }
        }
        System.out.printf("%n=== %s: %d scored rows ===%n", VARIANT, rows.size());

        for (String metric : List.of("sentiment", "grounded", "no_forbidden", "coverage", "safe")) {
            double p = rows.stream().mapToInt(r -> r.get("grade").get(metric).asInt()).average().orElse(0);
            System.out.printf("%-13s %s%n", metric, withInterval(p, Math.max(1, rows.size())));
        }
        // Per label, so "always POSITIVE" can't hide behind the 12 POSITIVE cases
        Map<String, List<JsonNode>> byLabel = rows.stream()
                .collect(Collectors.groupingBy(r -> r.get("meta").get("expected").asString()));
        byLabel.forEach((label, labelRows) -> System.out.printf("sentiment[%s] %d/%d%n", label,
                labelRows.stream().mapToInt(r -> r.get("grade").get("sentiment").asInt()).sum(), labelRows.size()));
    }

    // Wilson score interval: unlike p ± 1.96·sqrt(p(1-p)/n), it doesn't collapse to
    // ±0 at 0% or 100%
    private static String withInterval(double p, int n) {
        double z = 1.96, z2 = z * z;
        double centre = (p + z2 / (2 * n)) / (1 + z2 / n);
        double half = z * Math.sqrt(p * (1 - p) / n + z2 / (4.0 * n * n)) / (1 + z2 / n);
        return "%5.1f%%  (95%% CI %3.0f-%3.0f%%)".formatted(p * 100, (centre - half) * 100, (centre + half) * 100);
    }
}