package pt.isep.labdsof.gateway;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;

/** Check Keycloak session activity after local JWT validation and account rate limiting. */
final class SessionActivityFilter extends OncePerRequestFilter {

  private static final Logger log = LoggerFactory.getLogger(SessionActivityFilter.class);

  private static final PathPatternRequestMatcher API =
      PathPatternRequestMatcher.withDefaults().matcher("/api/v1/**");
  private static final PathPatternRequestMatcher STATUS =
      PathPatternRequestMatcher.withDefaults().matcher("/api/v1/status");

  private final RestClient identity;
  private final JsonMapper json;

  SessionActivityFilter(RestClient identity, JsonMapper json) {
    this.identity = identity;
    this.json = json;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !API.matches(request) || STATUS.matches(request);
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (!(SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken token)) {
      chain.doFilter(request, response);
      return;
    }

    var form = new LinkedMultiValueMap<String, String>();
    form.add("token", token.getToken().getTokenValue());
    form.add("token_type_hint", "access_token");
    TokenActivity activity;
    try {
      activity = identity.post().contentType(MediaType.APPLICATION_FORM_URLENCODED)
          .accept(MediaType.APPLICATION_JSON)
          .body(form).retrieve().body(TokenActivity.class);
    } catch (RestClientException failure) {
      // The exception message can include an identity-provider response body. Log only safe diagnostics.
      var warning = log.atWarn().addKeyValue("failureType", failure.getClass().getSimpleName());
      if (failure instanceof RestClientResponseException upstream) {
        warning.addKeyValue("identityStatus", upstream.getStatusCode().value());
      }
      warning.log("Keycloak token introspection failed");
      unavailable(response);
      return;
    }

    if (activity == null || activity.active() == null) {
      log.atWarn().addKeyValue("failureType", "MISSING_ACTIVE").log("Keycloak token introspection failed");
      unavailable(response);
    } else if (Boolean.FALSE.equals(activity.active())) {
      response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"invalid_token\"");
      problem(response, HttpStatus.UNAUTHORIZED, "The session has ended or the access token is inactive.");
    } else {
      chain.doFilter(request, response);
    }
  }

  private void unavailable(HttpServletResponse response) throws IOException {
    response.setHeader(HttpHeaders.RETRY_AFTER, "5");
    problem(response, HttpStatus.SERVICE_UNAVAILABLE, "Session validation is temporarily unavailable.");
  }

  private void problem(HttpServletResponse response, HttpStatus status, String detail) throws IOException {
    response.setStatus(status.value());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    json.writeValue(response.getOutputStream(), ProblemDetail.forStatusAndDetail(status, detail));
  }

  record TokenActivity(Boolean active) {}
}
