package dev.naughlan.movies.ai;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import dev.naughlan.movies.security.SecurityConfig;

@WebMvcTest(MovieDigestController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class MovieDigestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReviewDigestService digestService;

    @Test
    void anyoneCanReadAStoredDigestWithoutLoggingIn() throws Exception {
        ReviewDigest digest = new ReviewDigest("Loud, divisive, fun.", List.of("Cruise"), List.of("Too long"),
                "Fans of satire", ReviewDigest.Sentiment.MIXED);
        when(digestService.find("tt3915174")).thenReturn(Optional.of(new StoredDigest(
                "tt3915174", digest, 4, "claude-opus-5-5", 3019L, 322L, Instant.now(), Instant.now(), "hash")));

        mockMvc.perform(get("/api/v1/movies/tt3915174/digest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.digest.verdict").value("Loud, divisive, fun."))
                .andExpect(jsonPath("$.refreshing").value(true))
                // Internal details stay internal
                .andExpect(jsonPath("$.inputTokens").doesNotExist());
    }

    @Test
    void withoutADigestItIs404() throws Exception {
        when(digestService.find("tt0000001")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/movies/tt0000001/digest"))
                .andExpect(status().isNotFound());
    }
}