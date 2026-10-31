package pt.isep.labdsof.reports;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class StatusEndpointTest {

  @LocalServerPort private int port;

  @Test
  void statusEndpointReturnsTheDocumentedJsonOverHttp() throws Exception {
    var request = HttpRequest.newBuilder()
        .uri(URI.create("http://127.0.0.1:" + port + "/api/v1/status"))
        .timeout(Duration.ofSeconds(10))
        .GET()
        .build();

    try (var client = HttpClient.newHttpClient()) {
      var response = client.send(request, HttpResponse.BodyHandlers.ofString());
      assertThat(response.statusCode()).isEqualTo(200);
      assertThat(response.headers().firstValue("content-type").orElse(""))
          .startsWith("application/json");
      Map<String, Object> body = JsonPath.read(response.body(), "$");
      assertThat(body).containsExactlyInAnyOrderEntriesOf(
          Map.of("service", "reports", "status", "ok"));
    }
  }

  @Test
  void swaggerUiLoadsTheHandwrittenContract() throws Exception {
    try (var client = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL).build()) {
      var ui = client.send(get("/swagger-ui.html"), HttpResponse.BodyHandlers.ofString());
      assertThat(ui.statusCode()).isEqualTo(200);
      assertThat(ui.body()).contains("Swagger UI");

      var config = client.send(get("/v3/api-docs/swagger-config"),
          HttpResponse.BodyHandlers.ofString());
      assertThat(config.statusCode()).isEqualTo(200);
      String contractUrl = JsonPath.read(config.body(), "$.url");
      assertThat(contractUrl).isEqualTo("/openapi.yaml");

      var contract = client.send(get(contractUrl), HttpResponse.BodyHandlers.ofString());
      assertThat(contract.statusCode()).isEqualTo(200);
      assertThat(contract.body()).isEqualTo(Files.readString(Path.of("contracts/openapi.yaml")));

      var generated = client.send(get("/v3/api-docs"), HttpResponse.BodyHandlers.ofString());
      assertThat(generated.statusCode()).isEqualTo(404);
    }
  }

  private HttpRequest get(String path) {
    return HttpRequest.newBuilder()
        .uri(URI.create("http://127.0.0.1:" + port + path))
        .timeout(Duration.ofSeconds(10))
        .GET()
        .build();
  }

}
