package dev.naughlan.movies.ai;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
@ConditionalOnProperty(name = "app.ai.provider", havingValue = "open-model")
class OpenModelDigestModel implements DigestModel {

    // ReviewDigest as a JSON Schema, written out by hand (OpenModelDigestModelTest fails if the two drift apart).
    // vLLM turns it into a grammar and, at every step, masks out tokens that would break it, so the answer
    // always parses. Property order matters: the model writes the fields in this order.
    static final String SCHEMA = """
            {
              "type": "object",
              "properties": {
                "verdict": {"type": "string",
                  "description": "2 to 3 sentences: the overall verdict and the main reasons for it"},
                "liked": {"type": "array", "items": {"type": "string"},
                  "description": "Up to 3 things viewers liked, each a short phrase. Empty if none."},
                "disliked": {"type": "array", "items": {"type": "string"},
                  "description": "Up to 3 things viewers disliked, each a short phrase. Empty if none."},
                "watchIf": {"type": "string",
                  "description": "One sentence: who will enjoy this movie. Empty if the reviews don't say."},
                "sentiment": {"type": "string", "enum": ["POSITIVE", "MIXED", "NEGATIVE", "UNKNOWN"],
                  "description": "Overall mood of the reviews. UNKNOWN when there are too few reviews to judge."}
              },
              "required": ["verdict", "liked", "disliked", "watchIf", "sentiment"],
              "additionalProperties": false
            }""";

    // The grammar forces the shape but tells the model nothing about what each field means; Claude's API adds
    // the schema to the prompt for us, here we do it ourselves
    private static final String SCHEMA_INSTRUCTION =
            "\n\nAnswer with one JSON object that matches this JSON Schema:\n" + SCHEMA;

    // Only the parts of the OpenAI response we use
    record ChatResponse(String model, List<Choice> choices, Usage usage) {
    }

    record Choice(Message message, @JsonProperty("finish_reason") String finishReason) {
    }

    record Message(String content) {
    }

    record Usage(@JsonProperty("prompt_tokens") long promptTokens,
            @JsonProperty("completion_tokens") long completionTokens) {
    }

    private final RestClient restClient;
    private final AiProperties.OpenModel properties;
    private final JsonMapper jsonMapper;
    private final JsonNode schema;
    private final ObservationRegistry observations;

    OpenModelDigestModel(RestClient.Builder builder, AiProperties properties, JsonMapper jsonMapper,
            ObservationRegistry observations) {
        this.properties = properties.openModel();
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(2));
        requestFactory.setReadTimeout(this.properties.timeout());
        this.restClient = builder.clone()
                .baseUrl(this.properties.baseUrl())
                .requestFactory(requestFactory)
                .build();
        this.jsonMapper = jsonMapper;
        this.schema = jsonMapper.readTree(SCHEMA);
        this.observations = observations;
    }

    @Override
    public Result summarize(String systemPrompt, String userPrompt) {
        // The same observation as ClaudeDigestModel, so both models sit side by side in Grafana
        return Observation.createNotStarted("app.ai.model.call", observations)
                .contextualName("vllm " + properties.model())
                .lowCardinalityKeyValue("model", properties.model())
                .observe(() -> call(systemPrompt, userPrompt));
    }

    private Result call(String systemPrompt, String userPrompt) {
        Map<String, Object> body = Map.of(
                "model", properties.model(),
                "max_tokens", properties.maxTokens(),
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt + SCHEMA_INSTRUCTION),
                        Map.of("role", "user", "content", userPrompt)),
                "response_format", Map.of("type", "json_schema",
                        "json_schema", Map.of("name", "review_digest", "schema", schema, "strict", true)));

        long start = System.nanoTime();
        ChatResponse response = restClient.post()
                .uri("/v1/chat/completions")
                .body(body)
                .retrieve()
                .body(ChatResponse.class);
        long latencyMs = Duration.ofNanos(System.nanoTime() - start).toMillis();

        if (response == null || response.choices() == null || response.choices().isEmpty()) {
            throw new DigestNotProducedException("no choices in the response");
        }
        // Check WHY the model stopped before reading the answer: "length" means the JSON is cut off
        Choice choice = response.choices().getFirst();
        if ("length".equals(choice.finishReason())) {
            throw new DigestNotProducedException("hit max_tokens, so the JSON is cut off");
        }

        ReviewDigest digest;
        try {
            digest = jsonMapper.readValue(choice.message().content(), ReviewDigest.class);
        } catch (JacksonException e) {
            throw new DigestNotProducedException("the answer isn't a valid digest: " + e.getOriginalMessage());
        }
        return new Result(digest, response.model(), choice.finishReason(),
                response.usage().promptTokens(), response.usage().completionTokens(), latencyMs);
    }
}