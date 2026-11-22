package pt.isep.labdsof.events.adapters.in.web;

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
import pt.isep.labdsof.events.Correlation;

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
  @DisplayName("192 AC-8 a well-formed correlation id is kept")
  void wellFormedIdIsKept(String id) throws Exception {
    filter.doFilter(request(id), response, new MockFilterChain());

    assertThat(response.getHeader("X-Correlation-Id")).isEqualTo(id);
  }

  @Test
  @DisplayName("192 AC-8 a missing correlation id gets a new UUID")
  void missingIdGetsAUuid() throws Exception {
    filter.doFilter(request(null), response, new MockFilterChain());

    var id = response.getHeader("X-Correlation-Id");
    assertThat(UUID.fromString(id)).hasToString(id);
  }

  @ParameterizedTest(name = "[{index}] {0}")
  @ValueSource(strings = {"abcdefg", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "",
      "abcd efgh", "abcd_efgh", "ação12345", "abcdefgh\nlevel=ERROR", "abcdefgh\r\n"})
  @DisplayName("192 AC-8 a malformed, too short or too long correlation id, or one with a newline, gets a new UUID")
  void badIdIsReplaced(String id) throws Exception {
    filter.doFilter(request(id), response, new MockFilterChain());

    var replaced = response.getHeader("X-Correlation-Id");
    assertThat(replaced).isNotEqualTo(id);
    assertThat(UUID.fromString(replaced)).hasToString(replaced);
  }

  @Test
  @DisplayName("192 AC-8 the correlation id is in the MDC while the request runs and removed after it")
  void mdcHoldsTheIdWhileTheRequestRuns() throws Exception {
    var seen = new AtomicReference<String>();

    filter.doFilter(request("abcdefgh"), response,
        (req, res) -> seen.set(MDC.get(Correlation.MDC_KEY)));

    assertThat(seen.get()).isEqualTo("abcdefgh");
    assertThat(MDC.get(Correlation.MDC_KEY)).isNull();
  }

  @Test
  @DisplayName("192 AC-8 the MDC is cleaned up when the request fails")
  void mdcIsRemovedWhenTheChainThrows() {
    var failure = new ServletException("boom");

    assertThatThrownBy(() -> filter.doFilter(request("abcdefgh"), response, (req, res) -> {
      throw failure;
    })).isSameAs(failure);

    assertThat(MDC.get(Correlation.MDC_KEY)).isNull();
  }

  @Test
  @DisplayName("148 a request is logged with its method, path and status")
  @ExtendWith(OutputCaptureExtension.class)
  void requestIsLogged(CapturedOutput output) throws Exception {
    var request = new MockHttpServletRequest("GET", "/api/v1/events/e1");
    response.setStatus(404);

    filter.doFilter(request, response, new MockFilterChain());

    assertThat(output.getOut()).contains("GET /api/v1/events/e1 -> 404 in ");
  }

  @Test
  @DisplayName("148 an exception escaping the chain is logged as 500, not as the response's default 200")
  @ExtendWith(OutputCaptureExtension.class)
  void escapedExceptionIsLoggedAs500(CapturedOutput output) {
    var request = new MockHttpServletRequest("GET", "/api/v1/events/e1");

    assertThatThrownBy(() -> filter.doFilter(request, response, (req, res) -> {
      throw new ServletException("boom");
    })).isInstanceOf(ServletException.class);

    assertThat(output.getOut()).contains("GET /api/v1/events/e1 -> 500 in ");
  }

  @Test
  @DisplayName("148 the health check's status request is not logged, but still gets its correlation id")
  @ExtendWith(OutputCaptureExtension.class)
  void statusRequestIsNotLogged(CapturedOutput output) throws Exception {
    filter.doFilter(request("status-abcdefgh"), response, new MockFilterChain());

    assertThat(response.getHeader("X-Correlation-Id")).isEqualTo("status-abcdefgh");
    assertThat(output.getOut()).doesNotContain("/api/v1/status");
  }

  @Test
  @DisplayName("192 AC-8 the response header is set before the rest of the chain runs")
  void headerIsSetBeforeTheChainRuns() throws Exception {
    List<String> headerSeenByChain = new ArrayList<>();

    filter.doFilter(request("abcdefgh"), response,
        (req, res) -> headerSeenByChain.add(((MockHttpServletResponse) res).getHeader("X-Correlation-Id")));

    assertThat(headerSeenByChain).containsExactly("abcdefgh");
  }
}
