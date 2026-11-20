package pt.isep.labdsof.events;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;

/** #148 (plans/local-observability.md §2): the gateway's X-Correlation-Id is in every log line of the request. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "logging.pattern.console=[%X{correlationId}] %m%n")
@ExtendWith(OutputCaptureExtension.class)
class CorrelationIdTest {

  @LocalServerPort private int port;

  void get(String path, String correlationId) throws Exception {
    var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path));
    if (correlationId != null) {
      request.header("X-Correlation-Id", correlationId);
    }
    try (var client = HttpClient.newHttpClient()) {
      client.send(request.build(), HttpResponse.BodyHandlers.discarding());
    }
  }

  @Test
  @DisplayName("148 a valid X-Correlation-Id is in the request's log line")
  void keepsAValidId(CapturedOutput output) throws Exception {
    var id = "caller-" + UUID.randomUUID();
    get("/api/v1/events/e1", id);
    assertThat(output.getOut()).contains("[" + id + "] GET /api/v1/events/e1 -> 404 in ");
  }

  @Test
  @DisplayName("148 a missing, invalid or oversized X-Correlation-Id is replaced by a new UUID, never logged")
  void replacesAnInvalidId(CapturedOutput output) throws Exception {
    var uuidLine = Pattern.compile("(?m)^\\[[0-9a-f-]{36}] GET /api/v1/events/(bad\\d) -> 404 in ");
    get("/api/v1/events/bad1", null);
    get("/api/v1/events/bad2", "has space");
    get("/api/v1/events/bad3", "x".repeat(65));
    get("/api/v1/events/bad4", "short");
    assertThat(uuidLine.matcher(output.getOut()).results().map(m -> m.group(1)))
        .containsExactlyInAnyOrder("bad1", "bad2", "bad3", "bad4");
    assertThat(output.getOut()).doesNotContain("has space").doesNotContain("x".repeat(65));
  }

  @Test
  @DisplayName("148 the health check's status request is not logged")
  void statusNotLogged(CapturedOutput output) throws Exception {
    var id = "status-" + UUID.randomUUID();
    get("/api/v1/status", id);
    assertThat(output.getOut()).doesNotContain(id);
  }
}
