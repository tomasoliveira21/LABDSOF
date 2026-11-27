package pt.isep.labdsof.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
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
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** HTTP coverage for the gateway with a local key set and stub upstreams. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RouteTableTest {

  private static final List<String> SERVICES =
      List.of("auth-users", "events", "reports", "transports", "badges");
  // Each stub upstream answers "<service> <path it received>".
  private static final List<HttpServer> UPSTREAMS = SERVICES.stream().map(RouteTableTest::stub).toList();

  private static final String ISSUER = "https://issuer.example/realms/farol";
  private static final String WEB_ORIGIN = "http://localhost:3000";
  private static final RSAKey SIGNING_KEY = signingKey();
  private static final AtomicReference<String> INACTIVE = new AtomicReference<>();
  private static final AtomicReference<String> UNAVAILABLE = new AtomicReference<>();
  private static final HttpServer JWKS = jwksServer();

  @LocalServerPort private int port;

  @DynamicPropertySource
  static void routeTargets(DynamicPropertyRegistry registry) {
    registry.add("FAROL_ISSUER_URI", () -> ISSUER);
    registry.add("FAROL_INTROSPECTION_URI", () -> "http://127.0.0.1:" + JWKS.getAddress().getPort() + "/introspect");
    registry.add("FAROL_GATEWAY_CLIENT_SECRET", () -> "test-only");
    registry.add("FAROL_JWKS_URI", () -> "http://127.0.0.1:" + JWKS.getAddress().getPort());
    for (int i = 0; i < SERVICES.size(); i++) {
      String uri = "http://127.0.0.1:" + UPSTREAMS.get(i).getAddress().getPort();
      registry.add("FAROL_ROUTE_" + SERVICES.get(i).toUpperCase().replace('-', '_') + "_URI", () -> uri);
    }
  }

  @AfterAll
  static void stopUpstreams() {
    JWKS.stop(0);
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
    var response = path.startsWith("/status/")
        ? get(path)
        : get(path, token("route-test"));
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).isEqualTo(service + " " + upstreamPath);
  }

  @Test
  void unknownPathIsNotFoundAtTheGateway() throws Exception {
    assertThat(get("/api/v1/unknown", token("unknown-path-test")).statusCode()).isEqualTo(404);
  }

  @Test
  void gatewayStatusAnswers() throws Exception {
    assertThat(get("/api/v1/status").body()).isEqualTo("{\"service\":\"gateway\",\"status\":\"ok\"}");
  }

  @Test
  void protectedRouteRejectsMissingToken() throws Exception {
    var response = get("/api/v1/me");
    assertThat(response.statusCode()).isEqualTo(401);
    assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying(value ->
        assertThat(value).startsWith("application/problem+json"));
    assertThat(response.headers().firstValue("WWW-Authenticate")).hasValueSatisfying(value ->
        assertThat(value).startsWith("Bearer"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"signature", "issuer", "audience", "expiry", "subject"})
  void protectedRouteRejectsInvalidToken(String failedCheck) throws Exception {
    var expiry = failedCheck.equals("expiry") ? Instant.now().minusSeconds(180) : Instant.now().plusSeconds(600);
    var claims = new JWTClaimsSet.Builder()
        .subject(failedCheck.equals("subject") ? " " : "invalid-token-test")
        .issuer(failedCheck.equals("issuer") ? "https://wrong.example" : ISSUER)
        .audience(failedCheck.equals("audience") ? "wrong-audience" : "farol-api")
        .expirationTime(Date.from(expiry))
        .build();
    var key = failedCheck.equals("signature") ? signingKey() : SIGNING_KEY;
    var response = get("/api/v1/me", sign(claims, key));
    assertThat(response.statusCode()).isEqualTo(401);
    assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying(value ->
        assertThat(value).startsWith("application/problem+json"));
  }

  @Test
  void rateLimitIsSharedAcrossPathsAndTokensForTheSameAccount() throws Exception {
    var accountToken = token("rate-limited-account");
    try (var client = HttpClient.newHttpClient()) {
      for (int i = 0; i < 120; i++) {
        var request = request("/api/v1/me").header("Authorization", "Bearer " + accountToken).GET().build();
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("X-RateLimit-Remaining")).contains(Integer.toString(119 - i));
      }
    }

    var rejected = get("/api/v1/events", token("rate-limited-account"));
    assertThat(rejected.statusCode()).isEqualTo(429);
    assertThat(rejected.headers().firstValue("Retry-After")).hasValueSatisfying(value ->
        assertThat(Long.parseLong(value)).isBetween(1L, 60L));
    assertThat(rejected.headers().firstValue("Content-Type")).hasValueSatisfying(value ->
        assertThat(value).startsWith("application/problem+json"));
    assertThat(rejected.body()).contains("\"status\":429");
    assertThat(get("/api/v1/events", token("another-account")).statusCode()).isEqualTo(200);
  }

  @Test
  void corsAllowsTheWebPreflightAndExposesRetryAfter() throws Exception {
    var preflight = send(request("/api/v1/me")
        .header("Origin", WEB_ORIGIN)
        .header("Access-Control-Request-Method", "GET")
        .header("Access-Control-Request-Headers", "Authorization, If-None-Match")
        .method("OPTIONS", HttpRequest.BodyPublishers.noBody()).build());
    assertThat(preflight.statusCode()).isEqualTo(200);
    assertThat(preflight.headers().firstValue("Access-Control-Allow-Origin")).contains(WEB_ORIGIN);
    assertThat(preflight.headers().firstValue("Access-Control-Allow-Credentials")).isEmpty();

    var response = send(request("/api/v1/me")
        .header("Origin", "http://localhost:5173")
        .header("Authorization", "Bearer " + token("cors-test")).GET().build());
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.headers().firstValue("Access-Control-Expose-Headers")).hasValueSatisfying(value ->
        assertThat(value).contains("Retry-After"));
  }

  @Test
  void corsRejectsAnUnknownOrigin() throws Exception {
    var response = send(request("/api/v1/me")
        .header("Origin", "http://untrusted.example")
        .header("Access-Control-Request-Method", "GET")
        .method("OPTIONS", HttpRequest.BodyPublishers.noBody()).build());
    assertThat(response.statusCode()).isEqualTo(403);
    assertThat(response.headers().firstValue("Access-Control-Allow-Origin")).isEmpty();
  }

  private HttpRequest.Builder request(String path) {
    return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
        .timeout(Duration.ofSeconds(10));
  }

  private HttpResponse<String> get(String path) throws Exception {
    return send(request(path).GET().build());
  }

  private HttpResponse<String> get(String path, String token) throws Exception {
    return send(request(path).header("Authorization", "Bearer " + token).GET().build());
  }

  private static HttpResponse<String> send(HttpRequest request) throws Exception {
    try (var client = HttpClient.newHttpClient()) {
      return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
  }

  private static String token(String subject) throws JOSEException {
    return sign(new JWTClaimsSet.Builder().issuer(ISSUER).subject(subject).audience("farol-api")
        .jwtID(UUID.randomUUID().toString()).expirationTime(Date.from(Instant.now().plusSeconds(600)))
        .build(), SIGNING_KEY);
  }

  private static String sign(JWTClaimsSet claims, RSAKey key) throws JOSEException {
    var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("gateway-test")
        .type(JOSEObjectType.JWT).build(), claims);
    jwt.sign(new RSASSASigner(key));
    return jwt.serialize();
  }

  private static RSAKey signingKey() {
    try {
      return new RSAKeyGenerator(2048).keyID("gateway-test").generate();
    } catch (JOSEException e) {
      throw new IllegalStateException(e);
    }
  }

  @Test
  void accessTokenStopsWorkingAsSoonAsItsSessionEnds() throws Exception {
    String bearer = token("logged-out-account");
    assertThat(get("/api/v1/me", bearer).statusCode()).isEqualTo(200);
    INACTIVE.set(bearer);
    try {
      var response = get("/api/v1/me", bearer);
      assertThat(response.statusCode()).isEqualTo(401);
      assertThat(response.headers().firstValue("WWW-Authenticate")).contains("Bearer error=\"invalid_token\"");
      assertThat(get("/api/v1/me", token("other-active-account")).statusCode()).isEqualTo(200);
    } finally {
      INACTIVE.set(null);
    }
  }

  @Test
  void identityProviderFailureNeverPassesTheTokenUpstream() throws Exception {
    String bearer = token("unavailable-idp-account");
    UNAVAILABLE.set(bearer);
    try {
      var response = get("/api/v1/me", bearer);
      assertThat(response.statusCode()).isEqualTo(503);
      assertThat(response.headers().firstValue("Retry-After")).contains("5");
      assertThat(get("/api/v1/status").statusCode()).isEqualTo(200);
    } finally {
      UNAVAILABLE.set(null);
    }
  }

  private static void introspect(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
    try (exchange) {
      var supplied = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
      var expectedAuth = "Basic " + java.util.Base64.getEncoder()
          .encodeToString("farol-gateway:test-only".getBytes(StandardCharsets.UTF_8));
      if (!"POST".equals(exchange.getRequestMethod()) || !supplied.contains("token_type_hint=access_token")
          || !expectedAuth.equals(exchange.getRequestHeaders().getFirst("Authorization"))) {
        exchange.sendResponseHeaders(400, -1);
        return;
      }
      String unavailable = UNAVAILABLE.get();
      if (unavailable != null && supplied.contains("token=" + unavailable)) {
        exchange.sendResponseHeaders(503, -1);
        return;
      }
      String inactive = INACTIVE.get();
      boolean active = inactive == null || !supplied.contains("token=" + inactive);
      byte[] body = ("{\"active\":" + active + "}").getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.sendResponseHeaders(200, body.length);
      exchange.getResponseBody().write(body);
    }
  }

  private static HttpServer jwksServer() {
    try {
      var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.createContext("/introspect", RouteTableTest::introspect);
      server.createContext("/", exchange -> {
        byte[] body = new JWKSet(SIGNING_KEY.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        try (var output = exchange.getResponseBody()) {
          output.write(body);
        }
      });
      server.start();
      return server;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
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
