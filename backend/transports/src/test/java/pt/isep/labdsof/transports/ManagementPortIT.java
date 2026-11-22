package pt.isep.labdsof.transports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * #148 (spec D12): health and metrics on the private management port, as infra/compose.yaml runs it; nothing else
 * opens, and the API port still needs a token for the actuator. The bus source is read once at start-up, so its
 * calls are in the metrics.
 */
class ManagementPortIT {

  private static ConfigurableApplicationContext transports;
  private static int managementPort;
  private static int apiPort;

  @BeforeAll
  static void start() {
    transports = Infrastructure.startTransports(Map.of(
        "management.server.port", "0",
        "management.endpoints.web.exposure.include", "health,prometheus",
        "farol.transport.source.enabled", "true",
        "farol.transport.source.url", Infrastructure.wiremockUrl(),
        "farol.transport.source.interval", "1h"));
    var environment = transports.getEnvironment();
    managementPort = environment.getRequiredProperty("local.management.port", Integer.class);
    apiPort = environment.getRequiredProperty("local.server.port", Integer.class);
  }

  @AfterAll
  static void stop() {
    transports.close();
  }

  private static HttpResponse<String> at(int port, String path) throws Exception {
    try (var client = HttpClient.newHttpClient()) {
      return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).build(),
          HttpResponse.BodyHandlers.ofString());
    }
  }

  @Test
  @DisplayName("193 D12 Prometheus scrapes the management port without a token")
  void scrapeWithoutToken() throws Exception {
    var metrics = at(managementPort, "/actuator/prometheus");

    assertThat(metrics.statusCode()).isEqualTo(200);
    assertThat(metrics.body()).contains("jvm_memory_used_bytes");
    assertThat(at(managementPort, "/actuator/health").statusCode()).isEqualTo(200);
  }

  @Test
  @DisplayName("193 D12 the management port opens nothing else, and the API port still needs a token for the "
      + "actuator")
  void nothingElseOpens() throws Exception {
    assertThat(at(managementPort, "/actuator/env").statusCode()).isNotEqualTo(200);
    assertThat(at(managementPort, "/api/v1/status").statusCode()).isNotEqualTo(200);
    assertThat(at(apiPort, "/actuator/prometheus").statusCode()).isEqualTo(401);
    assertThat(at(apiPort, "/actuator/health").statusCode()).isEqualTo(401);
  }

  @Test
  @DisplayName("193 D12 the calls to the bus source are measured (http.client.requests)")
  void sourceCallsAreMeasured() {
    await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
        assertThat(at(managementPort, "/actuator/prometheus").body()).contains("http_client_requests_seconds_count"));
  }
}
