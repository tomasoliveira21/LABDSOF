package pt.isep.labdsof.badges.adapters.in.web;

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

/** Associates each HTTP request and response with a validated correlation ID. */
final class CorrelationIdFilter extends OncePerRequestFilter {

  static final String HEADER = "X-Correlation-Id";
  private static final Pattern VALID = Pattern.compile("[A-Za-z0-9-]{8,64}");
  private static final Logger log = LoggerFactory.getLogger(CorrelationIdFilter.class);

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    var id = correlationId(request.getHeader(HEADER));
    response.setHeader(HEADER, id);
    MDC.put("correlationId", id);
    long start = System.nanoTime();
    try {
      chain.doFilter(request, response);
    } finally {
      // Method, path without the query string, status, duration
      log.info("{} {} -> {} in {} ms", request.getMethod(), request.getRequestURI(), response.getStatus(),
          (System.nanoTime() - start) / 1_000_000);
      MDC.remove("correlationId");
    }
  }

  static String correlationId(String header) {
    return header != null && VALID.matcher(header).matches() ? header : UUID.randomUUID().toString();
  }
}
