package dev.naughlan.movies.ai;

import static dev.naughlan.movies.TestUsers.movieFan42;
import static dev.naughlan.movies.TestUsers.movieFan43;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.anthropic.errors.AnthropicIoException;

import dev.naughlan.movies.ai.ReviewDigestService.DigestPreview;
import dev.naughlan.movies.security.SecurityConfig;

@WebMvcTest(ReviewDigestController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class ReviewDigestControllerTest {

    private static final String URL = "/api/v1/admin/movies/tt3915174/digest-preview";

    @Autowired
    private MockMvc mockMvc;

    // The service is mocked, so no test ever calls Claude (or spends money)
    @MockitoBean
    private ReviewDigestService digestService;

    @MockitoBean
    private AiProperties aiProperties;

    @Test
    void regularUsersGet403AndNothingIsGenerated() throws Exception {
        mockMvc.perform(post(URL).with(movieFan42()))
                .andExpect(status().isForbidden());

        verify(digestService, never()).preview(anyString());
    }

    @Test
    void adminsGetADigest() throws Exception {
        when(aiProperties.hasApiKey()).thenReturn(true);
        when(digestService.preview("tt3915174")).thenReturn(new DigestPreview(
                new ReviewDigest("Loud, divisive, fun.", List.of("Cruise"), List.of("Too long"), "Fans of satire",
                        ReviewDigest.Sentiment.MIXED),
                3, "claude-opus-5-5", "end_turn", 573, 264, 4200));

        mockMvc.perform(post(URL).with(movieFan43()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.digest.verdict").value("Loud, divisive, fun."))
                .andExpect(jsonPath("$.digest.sentiment").value("MIXED"))
                .andExpect(jsonPath("$.inputTokens").value(573));
    }

    @Test
    void withoutAnApiKeyItIs503() throws Exception {
        when(aiProperties.hasApiKey()).thenReturn(false);

        mockMvc.perform(post(URL).with(movieFan43()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value("ANTHROPIC_API_KEY is not configured"));

        verify(digestService, never()).preview(anyString());
    }

    @Test
    void whenClaudeIsUnreachableItIs502() throws Exception {
        when(aiProperties.hasApiKey()).thenReturn(true);
        when(digestService.preview("tt3915174")).thenThrow(new AnthropicIoException("timeout"));

        mockMvc.perform(post(URL).with(movieFan43()))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.detail").value("AI service unreachable"));
    }
}