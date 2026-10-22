package dev.naughlan.movies.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.sun.net.httpserver.HttpServer;

import io.micrometer.observation.ObservationRegistry;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Against a fake vLLM: a tiny HTTP server built into the JDK that records the request and returns a canned
 * OpenAI-style answer.
 */
class OpenModelDigestModelTest {

    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final AtomicReference<String> requestBody = new AtomicReference<>();
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) { // the schema test never starts one
            server.stop(0);
        }
    }

    @Test
    void returnsTheDigestTheModelWrote() {
        OpenModelDigestModel model = modelAnswering("""
                {"model": "qwen3-4b",
                 "choices": [{"finish_reason": "stop", "message": {"role": "assistant",
                   "content": "{\\"verdict\\":\\"Loved.\\",\\"liked\\":[\\"music\\"],\\"disliked\\":[],\\"watchIf\\":\\"\\",\\"sentiment\\":\\"POSITIVE\\"}"}}],
                 "usage": {"prompt_tokens": 900, "completion_tokens": 80}}""");

        DigestModel.Result result = model.summarize("Summarize.", "Review 1: Loved the music.");

        assertThat(result.digest()).isEqualTo(new ReviewDigest("Loved.", List.of("music"), List.of(), "",
                ReviewDigest.Sentiment.POSITIVE));
        assertThat(result.model()).isEqualTo("qwen3-4b");
        assertThat(result.inputTokens()).isEqualTo(900);
        assertThat(result.outputTokens()).isEqualTo(80);

        // What we sent: the configured model, the schema as a constraint, and the schema in the prompt too
        JsonNode sent = jsonMapper.readTree(requestBody.get());
        assertThat(sent.get("model").asString()).isEqualTo("qwen3-4b");
        assertThat(sent.get("response_format").get("json_schema").get("schema"))
                .isEqualTo(jsonMapper.readTree(OpenModelDigestModel.SCHEMA));
        assertThat(sent.get("messages").get(0).get("content").asString()).contains("\"watchIf\"");
    }

    @Test
    void anAnswerCutOffByMaxTokensIsNotADigest() {
        OpenModelDigestModel model = modelAnswering("""
                {"model": "qwen3-4b",
                 "choices": [{"finish_reason": "length", "message": {"content": "{\\"verdict\\":\\"Lov"}}],
                 "usage": {"prompt_tokens": 900, "completion_tokens": 1024}}""");

        assertThatThrownBy(() -> model.summarize("Summarize.", "Review 1: Loved the music."))
                .isInstanceOf(DigestNotProducedException.class)
                .hasMessageContaining("max_tokens");
    }

    // The hand-written schema must say what ReviewDigest says: same fields, same order, same descriptions
    @Test
    void theSchemaMatchesReviewDigest() {
        JsonNode properties = jsonMapper.readTree(OpenModelDigestModel.SCHEMA).get("properties");
        List<String> schemaFields = new ArrayList<>(properties.propertyNames());

        assertThat(schemaFields).containsExactlyElementsOf(Arrays.stream(ReviewDigest.class.getRecordComponents())
                .map(component -> component.getName()).toList());
        for (var component : ReviewDigest.class.getRecordComponents()) {
            assertThat(properties.get(component.getName()).get("description").asString())
                    .as(component.getName())
                    // The annotation can't sit on a record component itself, so Java copies it onto the accessor
                    .isEqualTo(component.getAccessor().getAnnotation(JsonPropertyDescription.class).value());
        }
        assertThat(properties.get("sentiment").get("enum").valueStream().map(JsonNode::asString))
                .containsExactly(Arrays.stream(ReviewDigest.Sentiment.values()).map(Enum::name).toArray(String[]::new));
    }

    private OpenModelDigestModel modelAnswering(String responseJson) {
        try {
            server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        server.createContext("/v1/chat/completions", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = responseJson.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();

        AiProperties properties = new AiProperties("open-model", null, null, 0, null, null, null,
                new AiProperties.OpenModel("http://localhost:" + server.getAddress().getPort(), "qwen3-4b", 1024,
                        Duration.ofSeconds(5)));
        return new OpenModelDigestModel(RestClient.builder(), properties, jsonMapper, ObservationRegistry.NOOP);
    }
}