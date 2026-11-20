package pt.isep.labdsof.gateway;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
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
 * #148 (plans/local-observability.md §2): every request gets one correlation id at the edge. A valid incoming
 * X-Correlation-Id is kept, anything else is replaced by a random UUID. The id goes to the service it is routed to,
 * back to the caller and, as {@code correlationId}, into every log line of the request. One log line per request
 * records the outcome; the health check's own status request is not logged.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class CorrelationIdFilter extends OncePerRequestFilter {

  static final String HEADER = "X-Correlation-Id";
  static final String MDC_KEY = "correlationId";
  /** 8 to 64 letters, digits or hyphens, as Auth & Users (#191 spec D8): never a log or header injection. */
  private static final Pattern VALID = Pattern.compile("[A-Za-z0-9-]{8,64}");
  private static final Logger log = LoggerFactory.getLogger(CorrelationIdFilter.class);

  static String accept(String incoming) {
    return incoming != null && VALID.matcher(incoming).matches() ? incoming : UUID.randomUUID().toString();
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    var id = accept(request.getHeader(HEADER));
    response.setHeader(HEADER, id);
    MDC.put(MDC_KEY, id);
    long start = System.nanoTime();
    // An exception escaping the chain becomes a 500 the response does not hold yet (as in Auth & Users)
    int status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
    try {
      chain.doFilter(new WithCorrelationId(request, id), response);
      status = response.getStatus();
    } finally {
      if (!"/api/v1/status".equals(request.getRequestURI())) {
        log.info("{} {} -> {} in {} ms", request.getMethod(), request.getRequestURI(), status,
            (System.nanoTime() - start) / 1_000_000);
      }
      MDC.remove(MDC_KEY);
    }
  }

  /** The request as the routes see it: X-Correlation-Id replaced by the accepted id. */
  private static final class WithCorrelationId extends HttpServletRequestWrapper {

    private final String id;

    WithCorrelationId(HttpServletRequest request, String id) {
      super(request);
      this.id = id;
    }

    @Override
    public String getHeader(String name) {
      return HEADER.equalsIgnoreCase(name) ? id : super.getHeader(name);
    }

    @Override
    public Enumeration<String> getHeaders(String name) {
      return HEADER.equalsIgnoreCase(name) ? Collections.enumeration(List.of(id)) : super.getHeaders(name);
    }

    @Override
    public Enumeration<String> getHeaderNames() {
      var names = Collections.list(super.getHeaderNames());
      if (names.stream().noneMatch(HEADER::equalsIgnoreCase)) {
        names.add(HEADER);
      }
      return Collections.enumeration(names);
    }
  }
}
