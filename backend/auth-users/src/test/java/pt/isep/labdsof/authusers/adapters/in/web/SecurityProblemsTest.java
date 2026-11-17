package pt.isep.labdsof.authusers.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.json.ProblemDetailJacksonMixin;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import tools.jackson.databind.json.JsonMapper;

class SecurityProblemsTest {

  private final JsonMapper json = JsonMapper.builder().addMixIn(ProblemDetail.class, ProblemDetailJacksonMixin.class)
      .build();
  private final SecurityProblems problems = new SecurityProblems(json);
  private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/me");
  private final MockHttpServletResponse response = new MockHttpServletResponse();

  private ProblemDetail body() throws Exception {
    return json.readValue(response.getContentAsString(), ProblemDetail.class);
  }

  @Test
  @DisplayName("191 D9 the 401 of the security layer is Problem Details with WWW-Authenticate Bearer")
  void unauthorizedIsProblemDetailsWithBearer() throws Exception {
    problems.commence(request, response, new InsufficientAuthenticationException("secret-detail"));

    assertThat(response.getStatus()).isEqualTo(401);
    assertThat(response.getHeader("WWW-Authenticate")).startsWith("Bearer");
    assertThat(response.getContentType()).startsWith("application/problem+json");
    var problem = body();
    assertThat(problem.getStatus()).isEqualTo(401);
    assertThat(problem.getDetail()).isEqualTo("A valid bearer token is required.");
    assertThat(problem.getInstance()).hasToString("/api/v1/me");
    assertThat(response.getContentAsString()).doesNotContain("secret-detail");
  }

  @Test
  @DisplayName("191 D9 an invalid token keeps Spring's Bearer error in WWW-Authenticate and the body does not echo it")
  void invalidTokenKeepsTheBearerError() throws Exception {
    var error = new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN, "Jwt expired at some-instant", null);

    problems.commence(request, response, new InvalidBearerTokenException(error.getDescription()));
    var viaOauth = new MockHttpServletResponse();
    problems.commence(request, viaOauth, new OAuth2AuthenticationException(error));

    assertThat(response.getHeader("WWW-Authenticate")).startsWith("Bearer error=\"invalid_token\"");
    assertThat(viaOauth.getHeader("WWW-Authenticate")).startsWith("Bearer error=\"invalid_token\"");
    assertThat(response.getContentAsString()).doesNotContain("some-instant");
    assertThat(body().getDetail()).isEqualTo("A valid bearer token is required.");
  }

  @Test
  @DisplayName("191 D9 the 403 of the security layer is Problem Details with WWW-Authenticate Bearer")
  void forbiddenIsProblemDetailsWithBearer() throws Exception {
    problems.handle(request, response, new AccessDeniedException("secret-detail"));

    assertThat(response.getStatus()).isEqualTo(403);
    assertThat(response.getHeader("WWW-Authenticate")).startsWith("Bearer");
    assertThat(response.getContentType()).startsWith("application/problem+json");
    assertThat(body().getStatus()).isEqualTo(403);
    assertThat(body().getDetail()).isEqualTo("You are not allowed to do this.");
    assertThat(response.getContentAsString()).doesNotContain("secret-detail");
  }
}
