package pt.isep.labdsof.events.adapters.in.web;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * The 401 and 403 of the security layer: Spring's bearer handlers set the status and WWW-Authenticate, then the body
 * is written as Problem Details (spec D1). The detail is fixed text: never the exception message and never the token.
 */
@Component
class SecurityProblems implements AuthenticationEntryPoint, AccessDeniedHandler {

  private final AuthenticationEntryPoint bearer = new BearerTokenAuthenticationEntryPoint();
  private final AccessDeniedHandler bearerDenied = new BearerTokenAccessDeniedHandler();
  private final JsonMapper json;

  SecurityProblems(JsonMapper json) {
    this.json = json;
  }

  @Override
  public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException e)
      throws IOException, ServletException {
    bearer.commence(request, response, e);
    write(request, response, HttpStatus.UNAUTHORIZED, "A valid bearer token is required.");
  }

  @Override
  public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException e)
      throws IOException, ServletException {
    bearerDenied.handle(request, response, e);
    write(request, response, HttpStatus.FORBIDDEN, "You are not allowed to do this.");
  }

  private void write(HttpServletRequest request, HttpServletResponse response, HttpStatus status, String detail)
      throws IOException {
    var problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setInstance(URI.create(request.getRequestURI()));
    response.setStatus(status.value());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    json.writeValue(response.getOutputStream(), problem);
  }
}
