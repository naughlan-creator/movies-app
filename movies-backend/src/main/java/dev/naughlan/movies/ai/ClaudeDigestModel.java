package dev.naughlan.movies.ai;

import java.time.Duration;

import org.springframework.stereotype.Component;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.beta.messages.BetaOutputConfig;
import com.anthropic.models.beta.messages.BetaStopReason;
import com.anthropic.models.beta.messages.MessageCreateParams;
import com.anthropic.models.beta.messages.StructuredMessage;
import com.anthropic.models.beta.messages.StructuredMessageCreateParams;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;

/**
 * DigestModel backed by Claude. The only class in the app that uses the
 * Anthropic SDK's message API.
 */
@Component
class ClaudeDigestModel implements DigestModel {

    private final AnthropicClient client;
    private final AiProperties properties;
    private final ObservationRegistry observations;

    ClaudeDigestModel(AnthropicClient client, AiProperties properties, ObservationRegistry observations) {
        this.client = client;
        this.properties = properties;
        this.observations = observations;
    }

    @Override
    public Result summarize(String systemPrompt, String userPrompt) {
        // The SDK's HTTP client isn't instrumented by Spring, so we wrap the call
        // ourselves:
        // a span in Tempo plus a latency timer (with an error tag on failures) in
        // Prometheus
        return Observation.createNotStarted("app.ai.model.call", observations)
                .contextualName("claude " + properties.model())
                .lowCardinalityKeyValue("model", properties.model())
                .observe(() -> call(systemPrompt, userPrompt));
    }

    public Result call(String systemPrompt, String userPrompt) {
        StructuredMessageCreateParams<ReviewDigest> params = MessageCreateParams.builder()
                .model(properties.model())
                .maxTokens(properties.maxTokens())
                // If a safety classifier declines, the API re-runs the request on a fallback
                // model by itself
                .addBeta("server-side-fallback-2026-07-01")
                .fallbacksDefault()
                .outputConfig(BetaOutputConfig.builder().effort(BetaOutputConfig.Effort.LOW).build())
                // The SDK derives a JSON Schema from the record; the model's answer must match
                // it
                .outputConfig(ReviewDigest.class)
                .system(systemPrompt)
                .addUserMessage(userPrompt)
                .build();

        long start = System.nanoTime();
        StructuredMessage<ReviewDigest> response = client.beta().messages().create(params);
        long latencyMs = Duration.ofNanos(System.nanoTime() - start).toMillis();

        // Check WHY the model stopped before reading the answer
        BetaStopReason stopReason = response.stopReason().orElse(null);
        if (BetaStopReason.REFUSAL.equals(stopReason)) {
            String category = response.stopDetails().flatMap(details -> details.category())
                    .map(Object::toString).orElse("unknown");
            throw new DigestNotProducedException("refused (" + category + "), also by the fallback");
        }
        if (BetaStopReason.MAX_TOKENS.equals(stopReason)) {
            throw new DigestNotProducedException("hit max_tokens, so the JSON is cut off");
        }

        ReviewDigest digest = response.content().stream()
                .flatMap(block -> block.text().stream())
                .map(textBlock -> textBlock.text())
                .findFirst()
                .orElseThrow(() -> new DigestNotProducedException("no digest in the response"));

        // model() is the model that actually answered: after a fallback, it's the
        // fallback model
        return new Result(digest, response.model().asString(), String.valueOf(stopReason),
                response.usage().inputTokens(), response.usage().outputTokens(), latencyMs);
    }
}