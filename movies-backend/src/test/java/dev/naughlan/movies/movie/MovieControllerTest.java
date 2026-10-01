package dev.naughlan.movies.movie;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import dev.naughlan.movies.common.JsonCache;
import dev.naughlan.movies.security.SecurityConfig;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MovieController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class MovieControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MovieService movieService;

    // The web slice has no Redis: use the real cache code with caching switched off (always loads)
    @TestConfiguration
    static class PassThroughCache {
        @Bean
        MovieCache movieCache() {
            return new MovieCache(new JsonCache(mock(StringRedisTemplate.class), JsonMapper.builder().build(), new SimpleMeterRegistry(), false, Duration.ZERO));
        }
    }

    @Test
    void returnsFirstPageWithDefaultSize() throws Exception {
        Movie movie = new Movie();
        movie.setImdbId("tt0000001");
        when(movieService.allMovies(0, 10))
                .thenReturn(new PageImpl<>(List.of(movie), PageRequest.of(0, 10), 1));

        mockMvc.perform(get("/api/v1/movies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].imdbId").value("tt0000001"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalItems").value(1));
    }

    @Test
    void rejectsPageSizeAboveTheLimit() throws Exception {
        mockMvc.perform(get("/api/v1/movies").param("size", "500"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.size").exists());

        verify(movieService, never()).allMovies(anyInt(), anyInt());
    }

    @Test
    void rejectsNonNumericPage() throws Exception {
        mockMvc.perform(get("/api/v1/movies").param("page", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.page").exists());
    }

    @Test
    void sendsHstsOnlyOverHttps() throws Exception {
        when(movieService.allMovies(0, 10)).thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));

        // secure(true) is what the request looks like once Caddy's X-Forwarded-Proto: https is trusted
        mockMvc.perform(get("/api/v1/movies").secure(true))
                .andExpect(header().string("Strict-Transport-Security", "max-age=0"));
        mockMvc.perform(get("/api/v1/movies"))
                .andExpect(header().doesNotExist("Strict-Transport-Security"));
    }
}