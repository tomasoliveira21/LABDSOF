package pt.isep.labdsof.reports.adapters.in.web;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Resource server (ADR-006 decision 3, spec D10): signature, issuer, audience and expiry are checked by
 * Spring Security from the spring.security.oauth2.resourceserver.jwt.* properties. Bearer tokens only.
 */
@Configuration
class SecurityConfiguration {

  @Bean
  SecurityFilterChain api(HttpSecurity http) throws Exception {
    return http
        .authorizeHttpRequests(auth -> auth
            .requestMatchers(HttpMethod.GET, "/api/v1/status", "/openapi.yaml", "/swagger-ui.html",
                "/swagger-ui/**", "/v3/api-docs/swagger-config").permitAll()
            .requestMatchers("/error").permitAll()
            .anyRequest().authenticated())
        .oauth2ResourceServer(oauth -> oauth.jwt(Customizer.withDefaults()))
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .csrf(csrf -> csrf.disable())
        .build();
  }
}
