package pt.isep.labdsof.reports;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.test.context.TestPropertySource;

/** #148: health and metrics on the private management port, as infra/compose.yaml runs it; nothing else opens. */
@TestPropertySource(properties = {
    "management.server.port=0",
    "management.endpoints.web.exposure.include=health,prometheus"})
class ManagementPortIT extends IntegrationTest {

  @LocalManagementPort int managementPort;

  HttpResponse<String> at(int port, String path) throws Exception {
    try (var client = HttpClient.newHttpClient()) {
      return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).build(),
          HttpResponse.BodyHandlers.ofString());
    }
  }

  @Test
  @DisplayName("148 Prometheus scrapes the management port without a token, outbox age included")
  void scrapeWithoutToken() throws Exception {
    var metrics = at(managementPort, "/actuator/prometheus");
    assertThat(metrics.statusCode()).isEqualTo(200);
    assertThat(metrics.body()).contains("farol_outbox_oldest_pending_seconds");
    assertThat(at(managementPort, "/actuator/health").statusCode()).isEqualTo(200);
  }

  @Test
  @DisplayName("148 the management port opens nothing else, and the API port still needs a token for the actuator")
  void nothingElseOpens() throws Exception {
    assertThat(at(managementPort, "/actuator/env").statusCode()).isNotEqualTo(200);
    assertThat(at(managementPort, "/api/v1/events/x/help-requests").statusCode()).isNotEqualTo(200);
    assertThat(at(port, "/actuator/prometheus").statusCode()).isEqualTo(401);
  }
}
