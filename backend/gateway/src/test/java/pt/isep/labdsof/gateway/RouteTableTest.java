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
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** ADR-013 decision 2: each path of the route table reaches its service. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RouteTableTest {

  private static final List<String> SERVICES =
      List.of("auth-users", "events", "reports", "transports", "badges");
  // Each stub upstream answers "<service> <path it received>".
  private static final List<HttpServer> UPSTREAMS = SERVICES.stream().map(RouteTableTest::stub).toList();

  @LocalServerPort private int port;

  @DynamicPropertySource
  static void routeTargets(DynamicPropertyRegistry registry) {
    for (int i = 0; i < SERVICES.size(); i++) {
      String uri = "http://127.0.0.1:" + UPSTREAMS.get(i).getAddress().getPort();
      registry.add("FAROL_ROUTE_" + SERVICES.get(i).toUpperCase().replace('-', '_') + "_URI", () -> uri);
    }
  }

  @AfterAll
  static void stopUpstreams() {
    UPSTREAMS.forEach(server -> server.stop(0));
  }

  @ParameterizedTest
  @CsvSource({
      "/api/v1/me/badges, badges, /api/v1/me/badges",
      "/api/v1/me/badges/preferences, badges, /api/v1/me/badges/preferences",
      "/api/v1/me, auth-users, /api/v1/me",
      "/api/v1/me/memberships, auth-users, /api/v1/me/memberships",
      "/api/v1/accounts/a1, auth-users, /api/v1/accounts/a1",
      "/api/v1/events/e1/members/u1, auth-users, /api/v1/events/e1/members/u1",
      "/api/v1/events/e1/help-requests, reports, /api/v1/events/e1/help-requests",
      "/api/v1/events/e1/reports/r1, reports, /api/v1/events/e1/reports/r1",
      "/api/v1/events/e1/field-tasks, reports, /api/v1/events/e1/field-tasks",
      "/api/v1/events/e1/triage-status, reports, /api/v1/events/e1/triage-status",
      "/api/v1/events/e1/transport-options, transports, /api/v1/events/e1/transport-options",
      "/api/v1/events/e1/bus-observations, transports, /api/v1/events/e1/bus-observations",
      "/api/v1/events/e1/transport-source-status, transports, /api/v1/events/e1/transport-source-status",
      "/api/v1/events, events, /api/v1/events",
      "/api/v1/events/e1/teams, events, /api/v1/events/e1/teams",
      "/status/auth-users, auth-users, /api/v1/status",
      "/status/events, events, /api/v1/status",
      "/status/reports, reports, /api/v1/status",
      "/status/transports, transports, /api/v1/status",
      "/status/badges, badges, /api/v1/status",
  })
  void pathReachesItsService(String path, String service, String upstreamPath) throws Exception {
    var response = get(path);
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).isEqualTo(service + " " + upstreamPath);
  }

  @Test
  void unknownPathIsNotFoundAtTheGateway() throws Exception {
    assertThat(get("/api/v1/unknown").statusCode()).isEqualTo(404);
  }

  @Test
  void gatewayStatusAnswers() throws Exception {
    assertThat(get("/api/v1/status").body()).isEqualTo("{\"service\":\"gateway\",\"status\":\"ok\"}");
  }

  private HttpResponse<String> get(String path) throws Exception {
    var request = HttpRequest.newBuilder()
        .uri(URI.create("http://127.0.0.1:" + port + path))
        .timeout(Duration.ofSeconds(10))
        .GET()
        .build();
    try (var client = HttpClient.newHttpClient()) {
      return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
  }

  private static HttpServer stub(String service) {
    try {
      var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.createContext("/", exchange -> {
        byte[] body = (service + " " + exchange.getRequestURI().getPath())
            .getBytes(StandardCharsets.UTF_8);
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
}
