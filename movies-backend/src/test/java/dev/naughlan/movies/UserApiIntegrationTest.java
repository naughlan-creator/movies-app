package dev.naughlan.movies;

import dev.naughlan.movies.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class UserApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void resetUsers() {
        userRepository.deleteAll();
    }

    @Test
    void registersUserAndNeverExposesThePasswordHash() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "Movie_Fan", "password": "correct horse battery staple"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("movie_fan"))
                .andExpect(jsonPath("$.roles[0]").value("USER"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist());

        assertThat(userRepository.findByUsername("movie_fan"))
                .get()
                .satisfies(user -> assertThat(user.passwordHash()).startsWith("{bcrypt}"));
    }

    @Test
    void sameUsernameInDifferentCaseIsAConflict() throws Exception {
        String body = """
                {"username": "%s", "password": "correct horse battery staple"}
                """;

        mockMvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON).content(body.formatted("alice")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON).content(body.formatted("ALICE")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Username taken"));
    }
}