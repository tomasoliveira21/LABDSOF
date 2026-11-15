package pt.isep.labdsof.badges;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class StatusEndpointTest {

  @LocalServerPort private int port;

  @Test
  void contextStartsAndStatusAnswers() throws Exception {
    var request = HttpRequest.newBuilder()
        .uri(URI.create("http://127.0.0.1:" + port + "/api/v1/status"))
        .timeout(Duration.ofSeconds(10))
        .GET()
        .build();

    try (var client = HttpClient.newHttpClient()) {
      var response = client.send(request, HttpResponse.BodyHandlers.ofString());
      assertThat(response.statusCode()).isEqualTo(200);
      assertThat(response.body()).isEqualTo("{\"service\":\"badges\",\"status\":\"ok\"}");
    }
  }
}
