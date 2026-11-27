package pt.isep.labdsof.gateway;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.client.RestClient;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import tools.jackson.databind.json.JsonMapper;

@Configuration(proxyBeanMethods = false)
class GatewaySecurity {

  @Bean
  SecurityFilterChain gatewayFilterChain(
      HttpSecurity http, JsonMapper json,
      @Value("${farol.gateway.allowed-origins}") List<String> allowedOrigins,
      @Value("${farol.gateway.requests-per-minute}") int requestsPerMinute,
      @Value("${farol.gateway.introspection-uri}") String introspectionUri,
      @Value("${farol.gateway.introspection-secret}") String introspectionSecret) throws Exception {
    var unauthorized = unauthorizedProblem(json);
    return http
        .cors(cors -> cors.configurationSource(corsSource(allowedOrigins)))
        // This stateless API only accepts explicit bearer tokens.
        .csrf(csrf -> csrf.disable()) // NOSONAR java:S4502
        .exceptionHandling(errors -> errors.authenticationEntryPoint(unauthorized))
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth -> auth
            // Compose health checks and the existing service diagnostics are public.
            .requestMatchers(HttpMethod.GET, "/api/v1/status", "/status/auth-users", "/status/events",
                "/status/reports", "/status/transports", "/status/badges").permitAll()
            .requestMatchers("/api/v1/**").authenticated()
            .anyRequest().permitAll())
        .oauth2ResourceServer(oauth -> oauth.jwt(Customizer.withDefaults())
            .authenticationEntryPoint(unauthorized))
        .addFilterAfter(new AccountRateLimitFilter(requestsPerMinute), BearerTokenAuthenticationFilter.class)
        .addFilterAfter(new SessionActivityFilter(introspectionClient(introspectionUri, introspectionSecret), json),
            AccountRateLimitFilter.class)
        .build();
  }

  private static RestClient introspectionClient(String uri, String secret) {
    var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(2)).build());
    factory.setReadTimeout(Duration.ofSeconds(2));
    return RestClient.builder().baseUrl(uri).requestFactory(factory)
        .defaultHeaders(headers -> headers.setBasicAuth("farol-gateway", secret))
        .build();
  }

  @Bean
  OAuth2TokenValidator<Jwt> accountSubjectRequired() {
    return token -> {
      var subject = token.getSubject();
      if (subject == null || subject.isBlank()) {
        return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Missing account subject", null));
      }
      return OAuth2TokenValidatorResult.success();
    };
  }

  private AuthenticationEntryPoint unauthorizedProblem(JsonMapper json) {
    var bearer = new BearerTokenAuthenticationEntryPoint();
    return (request, response, failure) -> {
      bearer.commence(request, response, failure);
      response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
      var problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "A valid bearer token is required.");
      json.writeValue(response.getOutputStream(), problem);
    };
  }

  private UrlBasedCorsConfigurationSource corsSource(List<String> allowedOrigins) {
    if (allowedOrigins.contains("*")) {
      throw new IllegalArgumentException("Gateway CORS requires explicit origins");
    }

    var config = new CorsConfiguration();
    config.setAllowedOrigins(allowedOrigins);
    config.setAllowedMethods(List.of("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    config.setAllowedHeaders(List.of("Authorization", "Content-Type", "If-None-Match"));
    config.setExposedHeaders(List.of("ETag", "Retry-After", "X-Correlation-Id", "X-RateLimit-Remaining"));
    config.setAllowCredentials(false);

    var source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/api/v1/**", config);
    return source;
  }
}
