package pt.isep.labdsof.authusers.adapters.in.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;
import pt.isep.labdsof.authusers.Correlation;

/**
 * Gives every request a correlation id (spec D8): the {@code X-Correlation-Id} header when it is 8 to 64 letters,
 * digits or hyphens, otherwise a new UUID. The id goes to the MDC (so every log line and the outbox row carry it) and
 * to the response. A rejected value is never logged.
 */
final class CorrelationIdFilter extends OncePerRequestFilter {

  static final String HEADER = "X-Correlation-Id";
  /** The Compose health check, every 5 s: logged at DEBUG, or it would push real requests out of the log. */
  static final String HEALTH_CHECK_PATH = "/api/v1/status";
  private static final Pattern VALID = Pattern.compile("[A-Za-z0-9-]{8,64}");
  private static final Logger log = LoggerFactory.getLogger(CorrelationIdFilter.class);

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    var id = correlationId(request.getHeader(HEADER));
    response.setHeader(HEADER, id);
    MDC.put(Correlation.MDC_KEY, id);
    long start = System.nanoTime();
    // When an exception escapes the chain the container has not set the error status yet, so the request is reported
    // as the 500 it becomes, never as the 200 the response still holds
    int status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
    try {
      chain.doFilter(request, response);
      status = response.getStatus();
    } finally {
      // Method, path without the query string (GET /accounts?email= must never be logged), status, duration
      long millis = (System.nanoTime() - start) / 1_000_000;
      var path = request.getRequestURI();
      if (HEALTH_CHECK_PATH.equals(path)) {
        log.debug("{} {} -> {} in {} ms", request.getMethod(), path, status, millis);
      } else {
        log.info("{} {} -> {} in {} ms", request.getMethod(), path, status, millis);
      }
      MDC.remove(Correlation.MDC_KEY);
    }
  }

  static String correlationId(String header) {
    return header != null && VALID.matcher(header).matches() ? header : UUID.randomUUID().toString();
  }
}
