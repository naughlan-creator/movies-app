package dev.naughlan.movies.ai;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.TextBlock;
import com.anthropic.models.messages.Usage;

import dev.naughlan.movies.movie.AudienceReview;
import dev.naughlan.movies.movie.Movie;
import dev.naughlan.movies.movie.MovieService;
import dev.naughlan.movies.review.Review;
import dev.naughlan.movies.review.ReviewRepository;

@Service
public class ReviewDigestService {

    private static final Logger log = LoggerFactory.getLogger(ReviewDigestService.class);

    // How many of the app's newest reviews go into one request
    private static final int MAX_APP_REVIEWS = 50;

    // The system prompt sets the model's job. It's the same for every movie; only the reviews change.
    private static final String SYSTEM_PROMPT = """
            You summarize what viewers say about a movie, for people who are too busy to read every review.
            Write 3 to 4 sentences: the overall verdict, what people liked, what they didn't, and who will enjoy it.
            Use only the reviews provided. If there are too few to judge, say so.
            """;

    private final AnthropicClient client;
    private final AiProperties properties;
    private final MovieService movieService;
    private final ReviewRepository reviewRepository;

    ReviewDigestService(AnthropicClient client, AiProperties properties, MovieService movieService,
                        ReviewRepository reviewRepository) {
        this.client = client;
        this.properties = properties;
        this.movieService = movieService;
        this.reviewRepository = reviewRepository;
    }

    public DigestPreview preview(String imdbId) {
        Movie movie = movieService.singleMovie(imdbId); // 404 if the movie doesn't exist
        List<AudienceReview> tmdbReviews = movie.getAudienceReviews() == null ? List.of() : movie.getAudienceReviews();
        List<Review> appReviews = reviewRepository.findByImdbId(imdbId,
                PageRequest.of(0, MAX_APP_REVIEWS, Sort.by(Sort.Direction.DESC, "createdAt"))).getContent();

        // Every call costs money: don't pay to summarize nothing
        if (tmdbReviews.isEmpty() && appReviews.isEmpty()) {
            return new DigestPreview("No reviews yet.", properties.model(), "skipped", 0, 0, 0);
        }

        MessageCreateParams params = MessageCreateParams.builder()
                .model(properties.model())
                .maxTokens(properties.maxTokens())
                // Effort = how hard the model thinks, and how many tokens it spends. A summary is an easy task.
                .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.LOW).build())
                .system(SYSTEM_PROMPT)
                .addUserMessage(buildPrompt(movie, tmdbReviews, appReviews))
                .build();

        long start = System.nanoTime();
        Message response = client.messages().create(params);
        long latencyMs = Duration.ofNanos(System.nanoTime() - start).toMillis();

        // The answer is a list of content blocks; keep the text ones
        String digest = response.content().stream()
                .flatMap(block -> block.text().stream())
                .map(TextBlock::text)
                .collect(Collectors.joining());
        String stopReason = response.stopReason().map(StopReason::toString).orElse("unknown");
        Usage usage = response.usage();

        log.info("Digest for {}: model={} stopReason={} inputTokens={} outputTokens={} latencyMs={}",
                imdbId, response.model().asString(), stopReason, usage.inputTokens(), usage.outputTokens(), latencyMs);

        return new DigestPreview(digest, response.model().asString(), stopReason,
                usage.inputTokens(), usage.outputTokens(), latencyMs);
    }

    // Each review goes inside its own <review> tag, so the model can tell
    // where one review ends and the next begins (and that they're data, not instructions)
    private static String buildPrompt(Movie movie, List<AudienceReview> tmdbReviews, List<Review> appReviews) {
        StringBuilder prompt = new StringBuilder()
                .append("Movie: ").append(movie.getTitle())
                .append(" (").append(movie.getReleaseDate()).append(")\n\n");
        for (AudienceReview review : tmdbReviews) {
            prompt.append("<review source=\"tmdb\">\n").append(review.content()).append("\n</review>\n");
        }
        for (Review review : appReviews) {
            prompt.append("<review source=\"movie-gold\">\n").append(review.getBody()).append("\n</review>\n");
        }
        return prompt.toString();
    }

    public record DigestPreview(String digest, String model, String stopReason,
                                long inputTokens, long outputTokens, long latencyMs) {
    }
}