package dev.naughlan.movies.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ModelAndView;

class RateLimitFilterTest {

    // Stands in for GlobalExceptionHandler: here we only care that the request was stopped with 429
    private final HandlerExceptionResolver resolver = (request, response, handler, ex) -> {
        response.setStatus(429);
        return new ModelAndView();
    };

    private final RateLimitFilter filter = new RateLimitFilter(new RateLimitProperties(true, 3, 1, 1000), resolver);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletResponse send(String method, String ip) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, "/api/v1/movies");
        request.setRemoteAddr(ip);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    @Test
    void allowsUpToTheLimitThenReturns429WithRetryAfter() throws Exception {
        assertThat(send("GET", "203.0.113.7").getStatus()).isEqualTo(200);
        assertThat(send("GET", "203.0.113.7").getStatus()).isEqualTo(200);
        MockHttpServletResponse third = send("GET", "203.0.113.7");
        assertThat(third.getStatus()).isEqualTo(200);
        assertThat(third.getHeader("X-RateLimit-Remaining")).isEqualTo("0");

        MockHttpServletResponse fourth = send("GET", "203.0.113.7");
        assertThat(fourth.getStatus()).isEqualTo(429);
        assertThat(Long.parseLong(fourth.getHeader("Retry-After"))).isBetween(1L, 60L);
    }

    @Test
    void writesHaveTheirOwnSmallerBudget() throws Exception {
        assertThat(send("POST", "203.0.113.8").getStatus()).isEqualTo(200);
        assertThat(send("POST", "203.0.113.8").getStatus()).isEqualTo(429);
        // Reading is still allowed: the write budget is separate
        assertThat(send("GET", "203.0.113.8").getStatus()).isEqualTo(200);
    }

    @Test
    void clientsAreLimitedIndependently() throws Exception {
        send("POST", "203.0.113.9");
        assertThat(send("POST", "203.0.113.9").getStatus()).isEqualTo(429);

        assertThat(send("POST", "198.51.100.1").getStatus()).isEqualTo(200);
    }

    @Test
    void loggedInUsersAreLimitedPerUserNotPerIp() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("user-a", null, "ROLE_USER"));
        assertThat(send("POST", "203.0.113.10").getStatus()).isEqualTo(200);

        // Same user from another network: same bucket, so still limited
        assertThat(send("POST", "198.51.100.2").getStatus()).isEqualTo(429);
    }

    @Test
    void ignoresNonApiPaths() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/swagger-ui.html");
        request.setRemoteAddr("203.0.113.11");
        for (int i = 0; i < 10; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, new MockFilterChain());
            assertThat(response.getStatus()).isEqualTo(200);
        }
    }
}
