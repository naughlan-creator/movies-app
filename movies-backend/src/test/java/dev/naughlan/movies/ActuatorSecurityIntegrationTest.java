package dev.naughlan.movies;

import static dev.naughlan.movies.TestUsers.movieFan42;
import static dev.naughlan.movies.TestUsers.movieFan43;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class ActuatorSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthProbesArePublicAndRevealNoDetails() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
        mockMvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist());
    }

    @Test
    void adminsSeeHealthDetails() throws Exception {
        mockMvc.perform(get("/actuator/health").with(movieFan43()))
                .andExpect(jsonPath("$.components.mongo.status").value("UP"))
                .andExpect(jsonPath("$.components.redis.status").value("UP"));
    }

    @Test
    void metricsAreForAdminsOnly() throws Exception {
        mockMvc.perform(get("/actuator/metrics")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/metrics").with(movieFan42())).andExpect(status().isForbidden());
        mockMvc.perform(get("/actuator/metrics/app.cache.requests").with(movieFan43())).andExpect(status().isOk());
    }
}
