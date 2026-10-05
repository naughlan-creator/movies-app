package dev.naughlan.movies.ai;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;

@Configuration
@EnableConfigurationProperties(AiProperties.class)
class AiConfig {

    // One client for the whole app. It holds an HTTP connection pool, so build it once and share it.
    // AnthropicClient is AutoCloseable, so Spring closes it on shutdown.
    @Bean
    AnthropicClient anthropicClient(AiProperties properties) {
        return AnthropicOkHttpClient.builder()
                .apiKey(properties.apiKey())
                .timeout(properties.timeout())
                // Retries 429 (rate limited), 5xx and network errors, with backoff
                .maxRetries(2)
                .build();
    }
}