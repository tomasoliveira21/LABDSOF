package pt.isep.labdsof.reports;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("demo")
class DemoHelpRequestTest {

  @LocalServerPort private int port;

  @Test
  void createsAndReadsTheSameSyntheticRequest() throws Exception {
    try (var client = HttpClient.newHttpClient()) {
      var created = client.send(post("""
          {"category":"Health","description":"Synthetic exercise near the entrance."}
          """), HttpResponse.BodyHandlers.ofString());
      assertThat(created.statusCode()).isEqualTo(201);
      assertThat(created.headers().firstValue("X-Demo-Mode")).contains("true");
      String id = JsonPath.read(created.body(), "$.id");
      assertThat(UUID.fromString(id)).isNotNull();
      assertThat((String) JsonPath.read(created.body(), "$.category")).isEqualTo("Health");
      assertThat((String) JsonPath.read(created.body(), "$.description"))
          .isEqualTo("Synthetic exercise near the entrance.");
      assertThat((String) JsonPath.read(created.body(), "$.status")).isEqualTo("Submitted");
      assertThat((String) JsonPath.read(created.body(), "$.triage.state")).isEqualTo("NotTriaged");
      assertThat((String) JsonPath.read(created.body(), "$.requesterId"))
          .isEqualTo("demo-participant");
      assertThat((String) JsonPath.read(created.body(), "$.eventId")).isEqualTo("demo-event");
      assertThat((Boolean) JsonPath.read(created.body(), "$.demo")).isTrue();
      String location = created.headers().firstValue("Location").orElseThrow();
      assertThat(location).isEqualTo("/api/v1/demo/help-requests/" + id);
      var read = client.send(get(location), HttpResponse.BodyHandlers.ofString());
      assertThat(read.statusCode()).isEqualTo(200);
      assertThat(read.body()).isEqualTo(created.body());
    }
  }

  @ParameterizedTest
  @MethodSource("invalidRequests")
  void rejectsInvalidOrSpoofedRequests(String body) throws Exception {
    try (var client = HttpClient.newHttpClient()) {
      var result = client.send(post(body), HttpResponse.BodyHandlers.ofString());
      assertThat(result.statusCode()).isEqualTo(400);
    }
  }

  static Stream<String> invalidRequests() {
    return Stream.of(
        "{\"category\":\"Unknown\",\"description\":\"Synthetic\"}",
        "{\"category\":\"Health\",\"description\":\"   \"}",
        "{\"description\":\"Synthetic\"}",
        "{\"category\":\"Health\",\"description\":\"" + "x".repeat(501) + "\"}",
        "{\"category\":\"Health\",\"description\":\"Synthetic\",\"requesterId\":\"other\"}");
  }

  @Test
  void unknownRequestReturnsNotFound() throws Exception {
    try (var client = HttpClient.newHttpClient()) {
      var result = client.send(get("/api/v1/demo/help-requests/" + UUID.randomUUID()),
          HttpResponse.BodyHandlers.ofString());
      assertThat(result.statusCode()).isEqualTo(404);
    }
  }

  private HttpRequest post(String body) {
    return HttpRequest.newBuilder()
        .uri(URI.create(baseUrl() + "/api/v1/demo/help-requests"))
        .timeout(Duration.ofSeconds(10))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(body))
        .build();
  }

  private HttpRequest get(String path) {
    return HttpRequest.newBuilder().uri(URI.create(baseUrl() + path))
        .timeout(Duration.ofSeconds(10)).GET().build();
  }

  private String baseUrl() { return "http://127.0.0.1:" + port; }
}
