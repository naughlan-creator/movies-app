package dev.naughlan.movies.search;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

import dev.naughlan.movies.movie.MovieSummaryResponse;
import dev.naughlan.movies.security.SecurityConfig;

@WebMvcTest(SearchController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class SearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SearchService searchService;

    private static SearchService.Hit hit(String imdbId, String title, double score) {
        return new SearchService.Hit(new MovieSummaryResponse(imdbId, title, null, null, null, null,
                List.of(), null, List.of(), null, null, 0), score);
    }

    @Test
    void anyoneCanSearchWithoutLoggingIn() throws Exception {
        when(searchService.search("a sad family film", 10)).thenReturn(List.of(hit("tt1", "Harbour Lights", 0.61)));

        mockMvc.perform(get("/api/v1/movies/search").param("q", "a sad family film"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].movie.title").value("Harbour Lights"))
                .andExpect(jsonPath("$[0].score").value(0.61));
    }

    @Test
    void aBlankQueryIs400AndNeverReachesTheGpu() throws Exception {
        mockMvc.perform(get("/api/v1/movies/search").param("q", "   "))
                .andExpect(status().isBadRequest());

        verify(searchService, never()).search(anyString(), anyInt());
    }

    @Test
    void anyoneCanAskForSimilarMovies() throws Exception {
        when(searchService.similar("tt1", 6)).thenReturn(List.of(hit("tt2", "Paper Moons", 0.55)));

        mockMvc.perform(get("/api/v1/movies/tt1/similar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].movie.imdbId").value("tt2"));
    }

    @Test
    void whenSearchIsDownItIs503() throws Exception {
        when(searchService.search("anything", 10)).thenThrow(new SearchUnavailableException(null));

        mockMvc.perform(get("/api/v1/movies/search").param("q", "anything"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value("Search is not available right now"));
    }
}
