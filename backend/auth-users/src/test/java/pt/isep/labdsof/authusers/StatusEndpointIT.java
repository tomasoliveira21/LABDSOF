package pt.isep.labdsof.authusers;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Sends a token because Boot's default resource-server chain secures every path until the web feature (F03)
 * makes /api/v1/status public; F03 removes the header.
 */
class StatusEndpointIT extends IntegrationTest {

  @Test
  @DisplayName("191 AC-1 the service starts on its migrated database and answers its status")
  void contextStartsAndStatusAnswers() throws Exception {
    var request = HttpRequest.newBuilder()
        .uri(URI.create("http://127.0.0.1:" + port + "/api/v1/status"))
        .header("Authorization", "Bearer " + JWKS.valid(UUID.randomUUID().toString()))
        .timeout(Duration.ofSeconds(10))
        .GET()
        .build();

    try (var client = HttpClient.newHttpClient()) {
      var response = client.send(request, HttpResponse.BodyHandlers.ofString());
      assertThat(response.statusCode()).isEqualTo(200);
      assertThat(response.body()).isEqualTo("{\"service\":\"auth-users\",\"status\":\"ok\"}");
    }
  }
}
