package pt.isep.labdsof.authusers.adapters.in.web;

import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Resource server (ADR-006): Boot's JwtDecoder checks signature, issuer, audience and expiry; every
 * {@link OAuth2TokenValidator} bean is added to those checks. Only the status and the contract are public.
 */
@Configuration
class SecurityConfiguration {

  @Bean
  SecurityFilterChain api(HttpSecurity http, SecurityProblems problems) {
    return http
        .authorizeHttpRequests(auth -> auth
            .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
            .requestMatchers(HttpMethod.GET, "/api/v1/status", "/openapi.yaml").permitAll()
            .anyRequest().authenticated())
        .oauth2ResourceServer(oauth -> oauth.jwt(Customizer.withDefaults())
            .authenticationEntryPoint(problems).accessDeniedHandler(problems))
        .exceptionHandling(e -> e.authenticationEntryPoint(problems).accessDeniedHandler(problems))
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        // CSRF needs a browser that sends credentials by itself (a cookie or a session); this API is stateless and
        // accepts only an Authorization bearer token. With CSRF on, a POST without a token gets 403, not 401.
        .csrf(csrf -> csrf.disable()) // NOSONAR java:S4502 - stateless bearer-token API, reason above
        .build();
  }

  /** Spec D4: the user id is the token's sub, so a sub that is not a UUID is refused on every endpoint. */
  @Bean
  OAuth2TokenValidator<Jwt> subjectIsUuid() {
    return new SubjectValidator();
  }
}
