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
import pt.isep.labdsof.authusers.adapters.out.persistence.JdbcOutbox;

/**
 * Gives every request a correlation id (spec D8): the {@code X-Correlation-Id} header when it is 8 to 64 letters,
 * digits or hyphens, otherwise a new UUID. The id goes to the MDC (so every log line and the outbox row carry it) and
 * to the response. A rejected value is never logged.
 */
final class CorrelationIdFilter extends OncePerRequestFilter {

  static final String HEADER = "X-Correlation-Id";
  private static final Pattern VALID = Pattern.compile("[A-Za-z0-9-]{8,64}");
  private static final Logger log = LoggerFactory.getLogger(CorrelationIdFilter.class);

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    var id = correlationId(request.getHeader(HEADER));
    response.setHeader(HEADER, id);
    MDC.put(JdbcOutbox.CORRELATION_ID_MDC_KEY, id);
    long start = System.nanoTime();
    try {
      chain.doFilter(request, response);
    } finally {
      // Method, path without the query string (GET /accounts?email= must never be logged), status, duration
      log.info("{} {} -> {} in {} ms", request.getMethod(), request.getRequestURI(), response.getStatus(),
          (System.nanoTime() - start) / 1_000_000);
      MDC.remove(JdbcOutbox.CORRELATION_ID_MDC_KEY);
    }
  }

  static String correlationId(String header) {
    return header != null && VALID.matcher(header).matches() ? header : UUID.randomUUID().toString();
  }
}
