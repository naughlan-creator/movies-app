package dev.naughlan.movies.security;

import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.HandlerExceptionResolver;

@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties(RateLimitProperties.class)
@Import(JwtDecoderConfig.class)
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver,
            RateLimitProperties rateLimits,
            @Value("${app.security.hsts-max-age-seconds}") long hstsMaxAgeSeconds) throws Exception {

        // Filters run before @RestControllerAdvice can see anything, so hand security
        // errors to it explicitly: 401/403 then use the same ProblemDetail format as everything else
        AuthenticationEntryPoint problemDetailEntryPoint = (request, response, ex) -> {
            // RFC 6750: a 401 must tell the client which authentication scheme to use
            response.setHeader("WWW-Authenticate", "Bearer");
            exceptionResolver.resolveException(request, response, null, ex);
        };

        http
                .cors(Customizer.withDefaults())
                // Stateless API with credentials in a header, not a cookie: nothing for CSRF to abuse
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // HSTS is only sent on HTTPS requests (i.e. via the TLS proxy). Browsers remember it per host
                // and refuse plain HTTP to that host afterwards, so localhost uses 0 ("remember nothing")
                // and production sets a year.
                .headers(headers -> headers.httpStrictTransportSecurity(hsts -> hsts
                        .maxAgeInSeconds(hstsMaxAgeSeconds)
                        .includeSubDomains(false)))
                .authorizeHttpRequests(auth -> auth
                        // "*" matches exactly one path segment; new sub-paths stay protected by default
                        .requestMatchers(HttpMethod.GET, "/api/v1/movies", "/api/v1/movies/*", "/api/v1/movies/*/reviews").permitAll()
                        // Sign-up and login happen on Keycloak's pages, so the API has no public write endpoints
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs*", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/error").permitAll()
                        // Load balancers and Kubernetes probe health without credentials; it reveals only UP/DOWN.
                        // Everything else under /actuator (metrics, details) is for admins.
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/actuator/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                        .authenticationEntryPoint(problemDetailEntryPoint))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint(problemDetailEntryPoint)
                        .accessDeniedHandler((request, response, ex) ->
                                exceptionResolver.resolveException(request, response, null, ex)));

        if (rateLimits.enabled()) {
            // After the bearer token is read, so authenticated callers are limited per user rather than per IP.
            // Created here rather than as a @Bean: a Filter bean would also be registered with the servlet
            // container and run twice.
            http.addFilterAfter(new RateLimitFilter(rateLimits, exceptionResolver), BearerTokenAuthenticationFilter.class);
        }

        return http.build();
    }

    // Keycloak puts realm roles in a flat "roles" claim (see the realm's protocol mapper);
    // this turns ["USER", "ADMIN"] into Spring authorities ROLE_USER and ROLE_ADMIN.
    // The principal name stays the default "sub" claim: the stable user id.
    private static JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("roles");
        authorities.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins}") List<String> allowedOrigins) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "DELETE"));
        config.setAllowedHeaders(List.of("Content-Type", "Authorization"));
        // Cross-origin JavaScript can only read response headers that are explicitly exposed
        config.setExposedHeaders(List.of("Retry-After", "X-RateLimit-Remaining"));
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}