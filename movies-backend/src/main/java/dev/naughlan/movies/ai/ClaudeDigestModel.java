package dev.naughlan.movies.ai;

import java.time.Duration;

import org.springframework.stereotype.Component;

import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.StructuredMessage;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.anthropic.models.messages.StructuredTextBlock;
import com.anthropic.models.messages.Usage;

/** DigestModel backed by Claude. The only class in the app that uses the Anthropic SDK's message API. */
@Component
class ClaudeDigestModel implements DigestModel {

    private final AnthropicClient client;
    private final AiProperties properties;

    ClaudeDigestModel(AnthropicClient client, AiProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public Result summarize(String systemPrompt, String userPrompt) {
        StructuredMessageCreateParams<ReviewDigest> params = MessageCreateParams.builder()
                .model(properties.model())
                .maxTokens(properties.maxTokens())
                .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.LOW).build())
                // The SDK derives a JSON Schema from the record; the model's answer must match it
                .outputConfig(ReviewDigest.class)
                .system(systemPrompt)
                .addUserMessage(userPrompt)
                .build();

        long start = System.nanoTime();
        StructuredMessage<ReviewDigest> response = client.messages().create(params);
        long latencyMs = Duration.ofNanos(System.nanoTime() - start).toMillis();

        ReviewDigest digest = response.content().stream()
                .flatMap(block -> block.text().stream())
                .map(StructuredTextBlock::text)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Claude returned no digest"));
        Usage usage = response.usage();

        return new Result(digest, response.model().asString(),
                response.stopReason().map(StopReason::toString).orElse("unknown"),
                usage.inputTokens(), usage.outputTokens(), latencyMs);
    }
}