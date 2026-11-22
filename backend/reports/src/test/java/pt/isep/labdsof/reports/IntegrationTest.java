package pt.isep.labdsof.reports;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import pt.isep.labdsof.reports.application.LocalCopies;
import pt.isep.labdsof.reports.domain.Membership;

/** One Spring context on the shared containers, reused by every *IT that extends this class. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class IntegrationTest {

  @DynamicPropertySource
  static void infrastructure(DynamicPropertyRegistry registry) {
    Infrastructure.properties().forEach((key, value) -> registry.add(key, () -> value));
  }

  @LocalServerPort protected int port;
  @Autowired protected JdbcClient jdbc;
  @Autowired protected LocalCopies copies;

  protected static final Jwks JWKS = Infrastructure.JWKS;

  protected void member(UUID eventId, String user, Membership.Role role, UUID... teams) {
    copies.upsertIfNewer(new Membership(eventId, user, role, Membership.Status.Active, Set.of(teams), 1));
  }

  protected HttpResponse<String> get(String path, String token) throws Exception {
    return send(builder(path, token).GET());
  }

  protected HttpResponse<String> post(String path, String token, String json) throws Exception {
    return send(builder(path, token).header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(json)));
  }

  protected HttpResponse<String> patch(String path, String token, String json) throws Exception {
    return send(builder(path, token).header("Content-Type", "application/json")
        .method("PATCH", HttpRequest.BodyPublishers.ofString(json)));
  }

  protected static String submitBody(UUID clientRequestId, String description) {
    return """
        {"clientRequestId":"%s","location":{"mapVersion":3,"x":0.42,"y":0.77},"description":"%s"}
        """.formatted(clientRequestId, description);
  }

  private HttpRequest.Builder builder(String path, String token) {
    var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
        .timeout(Duration.ofSeconds(10));
    return token == null ? builder : builder.header("Authorization", "Bearer " + token);
  }

  private static HttpResponse<String> send(HttpRequest.Builder request) throws Exception {
    try (var client = HttpClient.newHttpClient()) {
      return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
  }
}
