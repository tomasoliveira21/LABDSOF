package pt.isep.labdsof.badges;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * #148: health (readiness included, for the Compose health check) and metrics on the private management port, as
 * infra/compose.yaml runs it; nothing else opens. No token is ever checked here, so the issuer and keys are dummies.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "management.server.port=0",
    "management.endpoints.web.exposure.include=health,prometheus"})
class ManagementPortIT {

  private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse(
      "postgres:18.7-alpine3.24@sha256:b6972e1115a4ff8eba5d7f7d19184926d1e16d7d1136ae1e1a7ebf286da0f015")
      .asCompatibleSubstituteFor("postgres"))
      .withDatabaseName("badges").withUsername("badges").withPassword("test-only-badges-db");

  @LocalServerPort int port;
  @LocalManagementPort int managementPort;

  @BeforeAll
  static void startPostgres() {
    POSTGRES.start();
  }

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("FAROL_ISSUER_URI", () -> "https://keycloak.example/realms/farol");
    registry.add("FAROL_JWKS_URI", () -> "http://127.0.0.1:9/jwks");
  }

  HttpResponse<String> at(int port, String path) throws Exception {
    try (var client = HttpClient.newHttpClient()) {
      return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).build(),
          HttpResponse.BodyHandlers.ofString());
    }
  }

  @Test
  @DisplayName("148 Prometheus scrapes the management port without a token; readiness answers there too")
  void scrapeWithoutToken() throws Exception {
    var metrics = at(managementPort, "/actuator/prometheus");
    assertThat(metrics.statusCode()).isEqualTo(200);
    assertThat(metrics.body()).contains("jvm_memory_used_bytes");
    assertThat(at(managementPort, "/actuator/health/readiness").statusCode()).isEqualTo(200);
  }

  @Test
  @DisplayName("148 the management port opens nothing else, and the API port still needs a token for the actuator")
  void nothingElseOpens() throws Exception {
    assertThat(at(managementPort, "/actuator/env").statusCode()).isNotEqualTo(200);
    assertThat(at(managementPort, "/api/v1/me/badges").statusCode()).isNotEqualTo(200);
    assertThat(at(port, "/actuator/prometheus").statusCode()).isEqualTo(401);
  }
}
