package pt.isep.labdsof.gateway;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

final class AccountRateLimitFilter extends OncePerRequestFilter {

  private static final Duration WINDOW = Duration.ofMinutes(1);
  private static final long NANOS_PER_SECOND = Duration.ofSeconds(1).toNanos();
  private static final PathPatternRequestMatcher API =
      PathPatternRequestMatcher.withDefaults().matcher("/api/v1/**");
  private static final String TOO_MANY_REQUESTS = """
      {"type":"about:blank","title":"Too Many Requests","status":429,
       "detail":"The account has reached its request limit. Retry after the indicated time."}
      """;

  private final int requestsPerMinute;
  // After a full idle window the bucket would have refilled anyway.
  private final Cache<String, Bucket> accounts = Caffeine.newBuilder().expireAfterAccess(WINDOW).build();

  AccountRateLimitFilter(int requestsPerMinute) {
    if (requestsPerMinute <= 0) {
      throw new IllegalArgumentException("Gateway requests per minute must be positive");
    }
    this.requestsPerMinute = requestsPerMinute;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !API.matches(request);
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
      FilterChain chain) throws ServletException, IOException {
    if (!(SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken token)) {
      chain.doFilter(request, response);
      return;
    }

    var bucket = accounts.get(token.getToken().getSubject(), key -> Bucket.builder()
        .addLimit(limit -> limit.capacity(requestsPerMinute).refillIntervally(requestsPerMinute, WINDOW))
        .build());
    var probe = bucket.tryConsumeAndReturnRemaining(1);
    response.setHeader("X-RateLimit-Remaining", Long.toString(probe.getRemainingTokens()));
    if (probe.isConsumed()) {
      chain.doFilter(request, response);
      return;
    }

    long seconds = Math.ceilDiv(probe.getNanosToWaitForRefill(), NANOS_PER_SECOND);
    response.setStatus(429);
    response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(Math.max(1, seconds)));
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    response.getWriter().write(TOO_MANY_REQUESTS);
  }
}
