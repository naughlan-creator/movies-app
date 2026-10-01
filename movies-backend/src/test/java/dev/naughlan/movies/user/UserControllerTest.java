package dev.naughlan.movies.user;

import static dev.naughlan.movies.TestUsers.MOVIE_FAN_43_ID;
import static dev.naughlan.movies.TestUsers.movieFan43;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import dev.naughlan.movies.security.SecurityConfig;

@WebMvcTest(UserController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void meReturnsTheIdentityFromTheAccessToken() throws Exception {
        mockMvc.perform(get("/api/v1/users/me").with(movieFan43()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(MOVIE_FAN_43_ID))
                .andExpect(jsonPath("$.username").value("movie_fan_43"))
                .andExpect(jsonPath("$.roles", containsInAnyOrder("USER", "ADMIN")));
    }

    @Test
    void meWithoutATokenIs401AndNamesTheScheme() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.title").value("Authentication required"));
    }
}
