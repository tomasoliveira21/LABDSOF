package pt.isep.labdsof.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** #148 (plans/local-observability.md §2): one correlation id per request, set at the edge. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "logging.pattern.console=%X{correlationId} %m%n")
@ExtendWith(OutputCaptureExtension.class)
class CorrelationIdTest {

  // The stub Reports answers with the X-Correlation-Id it received ("none" without one).
  private static final HttpServer REPORTS = stub();

  private static final String PATH = "/status/reports";

  @LocalServerPort private int port;

  @DynamicPropertySource
  static void reportsRoute(DynamicPropertyRegistry registry) {
    registry.add("FAROL_ROUTE_REPORTS_URI", () -> "http://127.0.0.1:" + REPORTS.getAddress().getPort());
  }

  @AfterAll
  static void stopReports() {
    REPORTS.stop(0);
  }

  private static HttpServer stub() {
    try {
      var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.createContext("/", exchange -> {
        var received = exchange.getRequestHeaders().getFirst("X-Correlation-Id");
        var body = (received == null ? "none" : received).getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
      });
      server.start();
      return server;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  // A public route to Reports (its status), so no token is needed; /api/v1/** needs one since #190
  HttpResponse<String> get(String correlationId) throws Exception {
    return get(PATH, correlationId);
  }

  HttpResponse<String> get(String path, String correlationId) throws Exception {
    var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path));
    if (correlationId != null) {
      request.header("X-Correlation-Id", correlationId);
    }
    try (var client = HttpClient.newHttpClient()) {
      return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
  }

  @Test
  @DisplayName("148 a valid X-Correlation-Id reaches the service, comes back and is in the request's log line")
  void keepsAValidId(CapturedOutput output) throws Exception {
    var id = "caller-" + UUID.randomUUID();
    var response = get(id);
    assertThat(response.body()).isEqualTo(id);
    assertThat(response.headers().allValues("X-Correlation-Id")).containsExactly(id);
    assertLogged(output, id + " GET " + PATH + " -> 200 in ");
  }

  @Test
  @DisplayName("148 a request without an id gets a new UUID, the same at the service and in the response")
  void createsOne() throws Exception {
    var response = get(null);
    var id = response.headers().firstValue("X-Correlation-Id").orElseThrow();
    assertThat(UUID.fromString(id)).isNotNull();
    assertThat(response.body()).isEqualTo(id);
  }

  @Test
  @DisplayName("148 an invalid or oversized X-Correlation-Id is replaced, never forwarded")
  void replacesAnInvalidId() throws Exception {
    for (var bad : new String[] {"has space", "x".repeat(65), "semi;colon", "", "short"}) {
      var response = get(bad);
      var id = response.headers().firstValue("X-Correlation-Id").orElseThrow();
      assertThat(UUID.fromString(id)).as(bad).isNotNull();
      assertThat(response.body()).isEqualTo(id);
    }
  }

  @Test
  @DisplayName("148 a request the gateway refuses (401, no token) still carries and logs its correlation id")
  void refusalCarriesTheId(CapturedOutput output) throws Exception {
    var id = "refused-" + UUID.randomUUID();
    var response = get("/api/v1/events/e1/help-requests", id);
    assertThat(response.statusCode()).isEqualTo(401);
    assertThat(response.headers().allValues("X-Correlation-Id")).containsExactly(id);
    assertLogged(output, id + " GET /api/v1/events/e1/help-requests -> 401 in ");
  }

  /**
   * The filter logs after the chain returns, and the caller can already have the response by then (the 401 body
   * closes the stream), so the line is awaited for up to 5 s rather than read once.
   */
  static void assertLogged(CapturedOutput output, String lineStart) throws InterruptedException {
    var line = Pattern.compile("(?m)^" + Pattern.quote(lineStart));
    var deadline = System.nanoTime() + 5_000_000_000L;
    while (!line.matcher(output.getOut()).find() && System.nanoTime() < deadline) {
      Thread.sleep(50);
    }
    assertThat(output.getOut()).containsPattern(line);
  }
}
