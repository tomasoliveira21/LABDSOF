package pt.isep.labdsof.events.adapters.in.web;

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
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * #148 (plans/local-observability.md §2): the gateway's X-Correlation-Id becomes {@code correlationId} in every log
 * line of the request (and, once Events has an outbox, in the rows it writes, as in Auth & Users and Reports). An
 * invalid or missing one is replaced by a random UUID, so a caller cannot inject text into the logs. Runs first; one
 * log line per request records the outcome (the health check's status request is not logged).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class CorrelationIdFilter extends OncePerRequestFilter {

  private static final Pattern VALID = Pattern.compile("[A-Za-z0-9-]{8,64}");
  private static final Logger log = LoggerFactory.getLogger(CorrelationIdFilter.class);

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    var incoming = request.getHeader("X-Correlation-Id");
    MDC.put("correlationId",
        incoming != null && VALID.matcher(incoming).matches() ? incoming : UUID.randomUUID().toString());
    long start = System.nanoTime();
    // An exception escaping the chain becomes a 500 the response does not hold yet (as in Auth & Users)
    int status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
    try {
      chain.doFilter(request, response);
      status = response.getStatus();
    } finally {
      if (!"/api/v1/status".equals(request.getRequestURI())) {
        log.info("{} {} -> {} in {} ms", request.getMethod(), request.getRequestURI(), status,
            (System.nanoTime() - start) / 1_000_000);
      }
      MDC.remove("correlationId");
    }
  }
}
