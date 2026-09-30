package dev.naughlan.movies;

import dev.naughlan.movies.security.JwtProperties;
import dev.naughlan.movies.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

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
                                                {"username": "movie_fan_43", "password": "correct horse battery staple"}
                                                """))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.username").value("movie_fan_43"))
                                .andExpect(jsonPath("$.roles[0]").value("USER"))
                                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                                .andExpect(jsonPath("$.password").doesNotExist());

                assertThat(userRepository.findByUsername("movie_fan_43"))
                                .get()
                                .satisfies(user -> assertThat(user.passwordHash()).startsWith("{bcrypt}"));
        }

        @Test
        void sameUsernameInDifferentCaseIsAConflict() throws Exception {
                String body = """
                                {"username": "%s", "password": "correct horse battery staple"}
                                """;

                mockMvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON)
                                .content(body.formatted("alice")))
                                .andExpect(status().isCreated());

                mockMvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON)
                                .content(body.formatted("ALICE")))
                                .andExpect(status().isConflict())
                                .andExpect(jsonPath("$.title").value("Username taken"));
        }

        private static final String PASSWORD = "correct horse battery staple";

        private void register(String username) throws Exception {
                mockMvc.perform(post("/api/v1/users")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                                {"username": "%s", "password": "%s"}
                                                """.formatted(username, PASSWORD)))
                                .andExpect(status().isCreated());
        }

        @Autowired
        private JwtEncoder jwtEncoder;

        @Autowired
        private JwtProperties jwtProperties;

        private String login(String username, String password) throws Exception {
                String body = mockMvc.perform(post("/api/v1/auth/token")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                                {"username": "%s", "password": "%s"}
                                                """.formatted(username, password)))
                                .andExpect(status().isOk())
                                .andReturn().getResponse().getContentAsString();
                return JsonPath.read(body, "$.accessToken");
        }

        @Test
        void loginReturnsATokenThatAuthenticatesLaterRequests() throws Exception {
                register("movie_fan_43");
                String token = login("movie_fan_43", PASSWORD);

                mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.username").value("movie_fan_43"));
        }

        @Test
        void wrongPasswordAndUnknownUserGetIdenticalResponses() throws Exception {
                register("movie_fan_43");
                String body = """
                                {"username": "%s", "password": "not the right password"}
                                """;

                String wrongPassword = mockMvc.perform(post("/api/v1/auth/token")
                                .contentType(MediaType.APPLICATION_JSON).content(body.formatted("movie_fan_43")))
                                .andExpect(status().isUnauthorized())
                                .andReturn().getResponse().getContentAsString();

                String unknownUser = mockMvc.perform(post("/api/v1/auth/token")
                                .contentType(MediaType.APPLICATION_JSON).content(body.formatted("nobody_here")))
                                .andExpect(status().isUnauthorized())
                                .andReturn().getResponse().getContentAsString();

                assertThat(unknownUser).isEqualTo(wrongPassword);
        }

        @Test
        void tokenWithForgedRolesIsRejected() throws Exception {
                register("movie_fan_43");
                String[] parts = login("movie_fan_43", PASSWORD).split("\\.");

                // Rewrite the payload to claim ADMIN, but keep the original signature
                String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
                String forgedPayload = Base64.getUrlEncoder().withoutPadding()
                                .encodeToString(payload.replace("\"USER\"", "\"ADMIN\"")
                                                .getBytes(StandardCharsets.UTF_8));
                String forgedToken = parts[0] + "." + forgedPayload + "." + parts[2];

                mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + forgedToken))
                                .andExpect(status().isUnauthorized())
                                .andExpect(header().string("WWW-Authenticate", "Bearer"));
        }

        @Test
        void expiredTokenIsRejected() throws Exception {
                register("movie_fan_43");
                Instant anHourAgo = Instant.now().minus(Duration.ofHours(1));
                JwtClaimsSet claims = JwtClaimsSet.builder()
                                .issuer(jwtProperties.issuer())
                                .subject("movie_fan_43")
                                .issuedAt(anHourAgo)
                                .expiresAt(anHourAgo.plus(Duration.ofMinutes(15)))
                                .claim("roles", List.of("USER"))
                                .build();
                String expiredToken = jwtEncoder.encode(
                                JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                                .getTokenValue();

                mockMvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + expiredToken))
                                .andExpect(status().isUnauthorized());
        }

        @Test
        void regularUsersCannotListAccounts() throws Exception {
        mockMvc.perform(get("/api/v1/users").with(user("movie_fan_42")))
                .andExpect(status().isForbidden());
        }

        @Test
        void adminsCanListAccounts() throws Exception {
        register("movie_fan_43");
        register("movie_fan_42");

        mockMvc.perform(get("/api/v1/users").with(user("movie_fan_43").roles("USER", "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(2))
                .andExpect(jsonPath("$.items[0].username").value("movie_fan_42")) // sorted by username
                .andExpect(jsonPath("$.items[1].username").value("movie_fan_43"))
                .andExpect(jsonPath("$.items[0].passwordHash").doesNotExist());
        }
}