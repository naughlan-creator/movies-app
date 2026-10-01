package dev.naughlan.movies.tmdb;

import static dev.naughlan.movies.TestUsers.movieFan42;
import static dev.naughlan.movies.TestUsers.movieFan43;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import dev.naughlan.movies.security.SecurityConfig;

@WebMvcTest(TrendingSyncController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class TrendingSyncControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TrendingMoviesSync trendingMoviesSync;

    @MockitoBean
    private TmdbProperties tmdbProperties;

    @Test
    void regularUsersGet403AndNothingRuns() throws Exception {
        mockMvc.perform(post("/api/v1/admin/trending-sync").with(movieFan42()))
                .andExpect(status().isForbidden());

        verify(trendingMoviesSync, never()).syncTrendingMovies();
    }

    @Test
    void adminsCanRunTheSync() throws Exception {
        when(tmdbProperties.hasApiToken()).thenReturn(true);
        when(trendingMoviesSync.syncTrendingMovies()).thenReturn(10);

        mockMvc.perform(post("/api/v1/admin/trending-sync").with(movieFan43()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.synced").value(10));
    }

    @Test
    void withoutATmdbTokenItIs503() throws Exception {
        when(tmdbProperties.hasApiToken()).thenReturn(false);

        mockMvc.perform(post("/api/v1/admin/trending-sync").with(movieFan43()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value("TMDB_API_TOKEN is not configured"));
    }
}
