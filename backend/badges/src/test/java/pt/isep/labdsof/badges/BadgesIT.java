package pt.isep.labdsof.badges;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BadgesIT {

  private static final String ISSUER = "https://keycloak.example/realms/farol";
  private static final String BADGES = "/api/v1/me/badges";
  private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse(
      "postgres:18.7-alpine3.24@sha256:b6972e1115a4ff8eba5d7f7d19184926d1e16d7d1136ae1e1a7ebf286da0f015")
      .asCompatibleSubstituteFor("postgres"))
      .withDatabaseName("badges").withUsername("badges").withPassword("test-only-badges-db");

  private static HttpServer jwks;
  private static RSAKey signingKey;

  @LocalServerPort private int port;
  @Autowired private JdbcClient jdbc;
  @Autowired private JsonMapper json;

  @BeforeAll
  static void startInfrastructure() throws Exception {
    POSTGRES.start();
    signingKey = new RSAKeyGenerator(2048).keyID("badges-test").generate();
    jwks = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    jwks.createContext("/jwks", exchange -> {
      byte[] body = new JWKSet(signingKey.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.sendResponseHeaders(200, body.length);
      try (var output = exchange.getResponseBody()) {
        output.write(body);
      }
    });
    jwks.start();
  }

  @AfterAll
  static void stopJwks() {
    if (jwks != null) {
      jwks.stop(0);
    }
    // Testcontainers removes PostgreSQL after the Spring context has finished.
  }

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("FAROL_ISSUER_URI", () -> ISSUER);
    registry.add("FAROL_JWKS_URI", () -> "http://127.0.0.1:" + jwks.getAddress().getPort() + "/jwks");
  }

  @Test
  @DisplayName("US-14 AC-3 an account cannot read another account's event badges")
  void aNewAccountGetsAnEmptyListAndCannotSeeSomeoneElsesBadges() throws Exception {
    var owner = UUID.randomUUID();
    var event = UUID.randomUUID();
    insertBadge(owner, "ReportHistory", null, 1, "Wood");
    insertBadge(owner, "EventReporter", event, 3, "Bronze");

    var newAccount = get(BADGES, token(UUID.randomUUID().toString(), "farol-api", signingKey));
    assertThat(newAccount.statusCode()).isEqualTo(200);
    assertThat(newAccount.body()).isEqualTo("[]");

    var own = get(BADGES, token(owner.toString(), "farol-api", signingKey));
    assertThat(own.statusCode()).isEqualTo(200);
    var result = json.readTree(own.body());
    assertThat(result.size()).isEqualTo(2);
    assertThat(result.get(0).get("track").asString()).isEqualTo("EventReporter");
    assertThat(result.get(0).get("eventId").asString()).isEqualTo(event.toString());
    assertThat(result.get(0).get("qualifyingCount").asInt()).isEqualTo(3);
    assertThat(result.get(0).get("tier").asString()).isEqualTo("Bronze");
    assertThat(result.get(1).get("track").asString()).isEqualTo("ReportHistory");

    assertThatThrownBy(() -> insertBadge(owner, "ReportHistory", null, 2, "Wood"))
        .isInstanceOf(DuplicateKeyException.class);
  }

  @Test
  void theServiceChecksTheTokenIndependentlyOfTheGateway() throws Exception {
    String subject = UUID.randomUUID().toString();
    var untrusted = new RSAKeyGenerator(2048).keyID("badges-test").generate();
    for (String invalid : new String[] {null, token(subject, "farol-api", untrusted),
        token(subject, "wrong-audience", signingKey), token("not-a-uuid", "farol-api", signingKey)}) {
      var response = get(BADGES, invalid);
      assertThat(response.statusCode()).isEqualTo(401);
      assertThat(response.headers().firstValue("Content-Type")).contains("application/problem+json");
      assertThat(response.headers().firstValue("X-Correlation-Id")).isPresent();
    }
  }

  @Test
  void statusReadinessAndContractAreAvailableAndFlywayAppliedBothMigrations() throws Exception {
    var status = get("/api/v1/status", null);
    assertThat(status.statusCode()).isEqualTo(200);
    assertThat(status.body()).isEqualTo("{\"service\":\"badges\",\"status\":\"ok\"}");
    assertThat(get("/actuator/health/readiness", null).statusCode()).isEqualTo(200);
    var contract = get("/openapi.yaml", null);
    assertThat(contract.statusCode()).isEqualTo(200);
    assertThat(contract.body()).contains("/api/v1/me/badges/preferences:", "operationId: putMyBadgePreferences");
    assertThat(jdbc.sql("SELECT current_database()").query(String.class).single()).isEqualTo("badges");
    assertThat(jdbc.sql("SELECT installed_rank FROM flyway_schema_history WHERE success ORDER BY installed_rank")
        .query(Integer.class).list()).containsExactly(1, 2);
    assertThat(jdbc.sql("SELECT count(*) FROM outbox").query(Long.class).single()).isZero();
  }

  @Test
  void bothPreferenceOperationsRequireAValidTokenAndAreReservedForUs56() throws Exception {
    String path = BADGES + "/preferences";
    String body = "{\"earningEnabled\":false,\"visibility\":\"Private\"}";
    String token = token(UUID.randomUUID().toString(), "farol-api", signingKey);
    assertThat(get(path, null).statusCode()).isEqualTo(401);
    assertThat(send(path, null, body).statusCode()).isEqualTo(401);
    assertThat(get(path, token).statusCode()).isEqualTo(501);
    assertThat(send(path, token, body).statusCode()).isEqualTo(501);
  }

  private void insertBadge(UUID user, String track, UUID event, int count, String tier) {
    jdbc.sql("""
        INSERT INTO badge_progress (user_id, track, event_id, qualifying_count, tier)
        VALUES (:user, :track, :event, :count, :tier)
        """)
        .param("user", user).param("track", track).param("event", event)
        .param("count", count).param("tier", tier).update();
  }

  private HttpResponse<String> get(String path, String token) throws Exception {
    return send(path, token, null);
  }

  private HttpResponse<String> send(String path, String token, String body) throws Exception {
    var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
        .timeout(Duration.ofSeconds(10));
    if (token != null) {
      request.header("Authorization", "Bearer " + token);
    }
    if (body != null) {
      request.header("Content-Type", "application/json").PUT(HttpRequest.BodyPublishers.ofString(body));
    }
    try (var client = HttpClient.newHttpClient()) {
      return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
  }

  private static String token(String subject, String audience, RSAKey key) throws Exception {
    var claims = new JWTClaimsSet.Builder().issuer(ISSUER).subject(subject).audience(audience)
        .expirationTime(Date.from(Instant.now().plusSeconds(120))).build();
    var signed = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(), claims);
    signed.sign(new RSASSASigner(key));
    return signed.serialize();
  }
}
