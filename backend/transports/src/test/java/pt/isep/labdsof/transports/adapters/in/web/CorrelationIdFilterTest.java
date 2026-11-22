package pt.isep.labdsof.transports.adapters.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.servlet.ServletException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import pt.isep.labdsof.transports.Correlation;

@ExtendWith(OutputCaptureExtension.class)
class CorrelationIdFilterTest {

  private final CorrelationIdFilter filter = new CorrelationIdFilter();
  private final MockHttpServletResponse response = new MockHttpServletResponse();

  @AfterEach
  void clearMdc() {
    MDC.clear();
  }

  private MockHttpServletRequest request(String header) {
    var request = new MockHttpServletRequest("GET", "/api/v1/status");
    if (header != null) {
      request.addHeader("X-Correlation-Id", header);
    }
    return request;
  }

  @ParameterizedTest(name = "[{index}] {0}")
  @ValueSource(strings = {"abcdefgh", "12345678", "a-b-c-d-e-f-g-h",
      "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "3f0c9a3e-8a52-4e0b-9d57-1a1c6c6a9b11"})
  @DisplayName("193 AC-7 a well-formed correlation id is kept")
  void wellFormedIdIsKept(String id) throws Exception {
    filter.doFilter(request(id), response, new MockFilterChain());

    assertThat(response.getHeader("X-Correlation-Id")).isEqualTo(id);
  }

  @Test
  @DisplayName("193 AC-7 a missing correlation id gets a new UUID")
  void missingIdGetsAUuid() throws Exception {
    filter.doFilter(request(null), response, new MockFilterChain());

    var id = response.getHeader("X-Correlation-Id");
    assertThat(UUID.fromString(id)).hasToString(id);
  }

  @ParameterizedTest(name = "[{index}] {0}")
  @ValueSource(strings = {"abcdefg", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "",
      "abcd efgh", "abcd_efgh", "ação12345", "abcdefgh\nlevel=ERROR", "abcdefgh\r\n"})
  @DisplayName("193 AC-7 a malformed, too short or too long correlation id, or one with a newline, gets a new UUID")
  void badIdIsReplaced(String id) throws Exception {
    filter.doFilter(request(id), response, new MockFilterChain());

    var replaced = response.getHeader("X-Correlation-Id");
    assertThat(replaced).isNotEqualTo(id);
    assertThat(UUID.fromString(replaced)).hasToString(replaced);
  }

  @Test
  @DisplayName("193 AC-7 the correlation id is in the MDC while the request runs and removed after it")
  void mdcHoldsTheIdWhileTheRequestRuns() throws Exception {
    var seen = new AtomicReference<String>();

    filter.doFilter(request("abcdefgh"), response,
        (req, res) -> seen.set(MDC.get(Correlation.MDC_KEY)));

    assertThat(seen.get()).isEqualTo("abcdefgh");
    assertThat(MDC.get(Correlation.MDC_KEY)).isNull();
  }

  @Test
  @DisplayName("193 AC-7 the MDC is cleaned up when the request fails")
  void mdcIsRemovedWhenTheChainThrows() {
    var failure = new ServletException("boom");

    assertThatThrownBy(() -> filter.doFilter(request("abcdefgh"), response, (req, res) -> {
      throw failure;
    })).isSameAs(failure);

    assertThat(MDC.get(Correlation.MDC_KEY)).isNull();
  }

  @Test
  @DisplayName("193 D1 a request whose exception escapes the chain is logged as the 500 it becomes, not 200")
  void anEscapedExceptionIsLoggedAsFiveHundred(CapturedOutput output) {
    var request = new MockHttpServletRequest("GET", "/api/v1/me");
    var failure = new ServletException("boom");

    assertThatThrownBy(() -> filter.doFilter(request, response, (req, res) -> {
      throw failure;
    })).isSameAs(failure);

    assertThat(response.getStatus()).as("the container has not set the error status yet").isEqualTo(200);
    assertThat(output.getOut()).contains("GET /api/v1/me -> 500 in").doesNotContain("-> 200 in");
  }

  @Test
  @DisplayName("193 D1 a request that is answered is logged with the response status")
  void anAnsweredRequestIsLoggedWithItsStatus(CapturedOutput output) throws Exception {
    var request = new MockHttpServletRequest("GET", "/api/v1/me");

    filter.doFilter(request, response, (req, res) -> ((MockHttpServletResponse) res).setStatus(403));

    assertThat(output.getOut()).contains("GET /api/v1/me -> 403 in");
  }

  @Test
  @DisplayName("193 D1 the health check is not logged at INFO: every 5 s it would push out the real requests")
  void theHealthCheckIsNotLoggedAtInfo(CapturedOutput output) throws Exception {
    filter.doFilter(new MockHttpServletRequest("GET", "/api/v1/status"), response, new MockFilterChain());

    assertThat(output.getOut()).doesNotContain("/api/v1/status");
  }

  @Test
  @DisplayName("193 D1 the query string is never logged, whatever the status")
  void theQueryStringIsNeverLogged(CapturedOutput output) throws Exception {
    var request = new MockHttpServletRequest("GET", "/api/v1/accounts");
    request.setQueryString("email=ana@farol.test");

    filter.doFilter(request, response, new MockFilterChain());

    assertThat(output.getOut()).contains("GET /api/v1/accounts -> 200 in").doesNotContain("ana@farol.test");
  }

  @Test
  @DisplayName("193 AC-7 the response header is set before the rest of the chain runs")
  void headerIsSetBeforeTheChainRuns() throws Exception {
    List<String> headerSeenByChain = new ArrayList<>();

    filter.doFilter(request("abcdefgh"), response,
        (req, res) -> headerSeenByChain.add(((MockHttpServletResponse) res).getHeader("X-Correlation-Id")));

    assertThat(headerSeenByChain).containsExactly("abcdefgh");
  }
}
