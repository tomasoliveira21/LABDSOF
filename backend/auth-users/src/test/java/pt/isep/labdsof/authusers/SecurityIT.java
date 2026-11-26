package pt.isep.labdsof.authusers;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Authentication of every request and the 400/415 answers of the web layer, over real HTTP with signed tokens. */
class SecurityIT extends IntegrationTest {

  private static final UUID ID = UUID.fromString("11111111-2222-3333-4444-555555555555");

  private ApiClient api() {
    return new ApiClient(port);
  }

  private static String user() {
    return UUID.randomUUID().toString();
  }

  private long accounts() {
    return jdbc.sql("SELECT count(*) FROM user_account").query(Long.class).single();
  }

  private long outboxRows() {
    return jdbc.sql("SELECT count(*) FROM outbox").query(Long.class).single();
  }

  /** A 401 Problem Details with a Bearer challenge that repeats none of the given texts. */
  static void assertRefused(HttpResponse<String> response, String... mustNotContain) {
    ApiClient.assertProblem(response, 401, mustNotContain);
    assertThat(response.headers().firstValue("WWW-Authenticate")).hasValueSatisfying(
        challenge -> assertThat(challenge).startsWith("Bearer"));
  }

  @Test
  @DisplayName("191 AC-2 a request without a token gets 401 Problem Details with WWW-Authenticate Bearer")
  void noTokenIsRefused() {
    var accountsBefore = accounts();
    var outboxBefore = outboxRows();

    assertRefused(api().get("/api/v1/me", null));

    assertThat(accounts()).isEqualTo(accountsBefore);
    assertThat(outboxRows()).isEqualTo(outboxBefore);
  }

  @Test
  @DisplayName("191 AC-2 a token expired more than 60 s ago gets 401")
  void expiredTokenIsRefused() {
    var token = JWKS.expired(user(), Duration.ofSeconds(120));

    assertRefused(api().get("/api/v1/me", token), token);
  }

  @Test
  @DisplayName("191 AC-2 a token expired less than 60 s ago is still accepted (clock skew)")
  void slightlyExpiredTokenIsAccepted() {
    var response = api().get("/api/v1/me", JWKS.expired(user(), Duration.ofSeconds(30)));

    assertThat(response.statusCode()).isEqualTo(200);
  }

  @Test
  @DisplayName("191 AC-2 a token whose payload was changed after signing gets 401")
  void tamperedTokenIsRefused() {
    var token = Jwks.tampered(JWKS.valid(user()));

    assertRefused(api().get("/api/v1/me", token), token);
  }

  @Test
  @DisplayName("191 AC-2 a token signed by another key under the same key id gets 401")
  void wrongSignatureIsRefused() {
    var token = JWKS.wrongSignature(user());

    assertRefused(api().get("/api/v1/me", token), token);
  }

  @Test
  @DisplayName("191 AC-2 a token signed by an unknown key gets 401")
  void unknownKeyIsRefused() {
    var token = JWKS.unknownKey(user());

    assertRefused(api().get("/api/v1/me", token), token);
  }

  @Test
  @DisplayName("191 AC-2 a token from another issuer gets 401")
  void wrongIssuerIsRefused() {
    var token = JWKS.wrongIssuer(user());

    assertRefused(api().get("/api/v1/me", token), token);
  }

  @Test
  @DisplayName("191 AC-2 a token for another audience gets 401")
  void wrongAudienceIsRefused() {
    var token = JWKS.wrongAudience(user());

    assertRefused(api().get("/api/v1/me", token), token);
  }

  @Test
  @DisplayName("191 AC-2 an unsigned token (alg none) gets 401")
  void unsignedTokenIsRefused() {
    var token = JWKS.unsigned(user());

    assertRefused(api().get("/api/v1/me", token), token);
  }

  @Test
  @DisplayName("191 AC-2 a token signed with HS256 instead of RS256 gets 401")
  void hmacTokenIsRefused() {
    var token = JWKS.hmac(user());

    assertRefused(api().get("/api/v1/me", token), token);
  }

  @ParameterizedTest(name = "[{index}] {0}")
  @ValueSource(strings = {"Bearer", "Bearer not a jwt", "Bearer a.b.c", "Bearer not-a-jwt",
      "Basic dXNlcjpwYXNz", "Token {valid}"})
  @DisplayName("191 AC-2 a malformed Authorization header gets 401")
  void malformedHeaderIsRefused(String header) {
    var valid = JWKS.valid(user());

    assertRefused(api().send("GET", "/api/v1/me", header.replace("{valid}", valid)), valid);
  }

  private record TokenKind(String name, Function<String, String> token) {

    @Override
    public String toString() {
      return name;
    }
  }

  private static List<TokenKind> badTokens() {
    return List.of(
        new TokenKind("no token", subject -> null),
        new TokenKind("expired", subject -> JWKS.expired(subject, Duration.ofSeconds(120))),
        new TokenKind("same key id, other key", JWKS::wrongSignature),
        new TokenKind("unknown key", JWKS::unknownKey),
        new TokenKind("wrong issuer", JWKS::wrongIssuer),
        new TokenKind("wrong audience", JWKS::wrongAudience),
        new TokenKind("alg none", JWKS::unsigned),
        new TokenKind("tampered", subject -> Jwks.tampered(JWKS.valid(subject))),
        new TokenKind("sub not a uuid", subject -> JWKS.valid("not-a-uuid")));
  }

  private static Stream<Arguments> securedOperationsWithBadTokens() {
    return ContractIT.contractOperations().stream()
        .filter(operation -> !operation.equals("GET /api/v1/status"))
        .sorted()
        .flatMap(operation -> badTokens().stream().map(kind -> Arguments.of(operation, kind)));
  }

  private static String concretePath(String path) {
    return path.replace("{eventId}", UUID.randomUUID().toString()).replace("{userId}", UUID.randomUUID().toString())
        .replace("{teamId}", UUID.randomUUID().toString());
  }

  @ParameterizedTest(name = "[{index}] {0} with {1}")
  @MethodSource("securedOperationsWithBadTokens")
  @DisplayName("191 AC-2 every secured operation of the contract refuses a missing or invalid token with 401 "
      + "Problem Details")
  void everySecuredOperationRefusesBadTokens(String operation, TokenKind kind) {
    var method = operation.substring(0, operation.indexOf(' '));
    var path = concretePath(operation.substring(operation.indexOf(' ') + 1));
    if (path.equals("/api/v1/accounts")) {
      path += "?email=ana@farol.test";
    }
    var body = operation.equals("POST /api/v1/events/{eventId}/members")
        ? "{\"userId\":\"" + UUID.randomUUID() + "\",\"role\":\"EventAdmin\"}" : null;
    var token = kind.token().apply(user());

    var response = api().send(method, path, token == null ? null : "Bearer " + token, body);

    assertRefused(response, token == null ? new String[0] : new String[] {token});
    assertThat(response.body()).doesNotContain("ana@farol.test");
  }

  @Test
  @DisplayName("191 AC-2 GET /api/v1/status is public for the health check")
  void statusIsPublic() {
    var response = api().get("/api/v1/status", null);

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).isEqualTo("{\"service\":\"auth-users\",\"status\":\"ok\"}");
  }

  @Test
  @DisplayName("191 AC-2 the actuator health endpoint needs a token")
  void actuatorHealthNeedsAToken() {
    assertRefused(api().get("/actuator/health", null));
  }

  private String validToken() {
    return JWKS.valid(user());
  }

  private HttpResponse<String> post(String path, String body, String... headers) {
    return api().send("POST", path, "Bearer " + validToken(), body, headers);
  }

  private static String membersPath() {
    return "/api/v1/events/" + ID + "/members";
  }

  @Test
  @DisplayName("191 D9 an unknown JSON field gets 400 before the use case (mass assignment)")
  void unknownJsonFieldIsRefused() {
    var response = post(membersPath(), "{\"userId\":\"" + UUID.randomUUID() + "\",\"role\":\"EventAdmin\","
        + "\"isFarolAdmin\":true}");

    ApiClient.assertProblem(response, 400, "isFarolAdmin");
  }

  private static Stream<Arguments> malformedPaths() {
    var body = "{\"userId\":\"" + UUID.randomUUID() + "\",\"role\":\"EventAdmin\"}";
    var team = "/teams/" + UUID.randomUUID();
    var member = "/members/" + UUID.randomUUID();
    var event = "/api/v1/events/" + UUID.randomUUID();
    return Stream.of(
        Arguments.of("GET", "/api/v1/events/not-a-uuid/members", null),
        Arguments.of("POST", "/api/v1/events/not-a-uuid/members", body),
        Arguments.of("POST", "/api/v1/events/not-a-uuid/members/me", null),
        Arguments.of("PUT", "/api/v1/events/not-a-uuid" + member + team, null),
        Arguments.of("PUT", event + "/members/not-a-uuid" + team, null),
        Arguments.of("PUT", event + member + "/teams/not-a-uuid", null),
        Arguments.of("DELETE", "/api/v1/events/not-a-uuid" + member + team, null),
        Arguments.of("DELETE", event + "/members/not-a-uuid" + team, null),
        Arguments.of("DELETE", event + member + "/teams/not-a-uuid", null));
  }

  @ParameterizedTest(name = "[{index}] {0} {1}")
  @MethodSource("malformedPaths")
  @DisplayName("191 D9 a malformed UUID in a path gets 400 before the use case")
  void malformedPathUuidIsRefused(String method, String path, String body) {
    ApiClient.assertProblem(api().send(method, path, "Bearer " + validToken(), body), 400);
  }

  private static Stream<String> invalidEmails() {
    return Stream.of("not-an-email", "a".repeat(244) + "@farol.test", "' OR '1'='1",
        "ana@farol.test\r\nX-Injected: yes", "ana@farol.test,bob@farol.test");
  }

  @ParameterizedTest(name = "[{index}]")
  @MethodSource("invalidEmails")
  @DisplayName("191 D9 an invalid or missing e-mail in GET /accounts gets 400 before the 501")
  void invalidEmailIsRefused(String email) {
    var query = "?email=" + URLEncoder.encode(email, StandardCharsets.UTF_8);

    var response = api().get("/api/v1/accounts" + query, validToken());

    ApiClient.assertProblem(response, 400, email, "ana@farol.test", "OR '1'");
  }

  @Test
  @DisplayName("191 D9 an invalid or missing e-mail in GET /accounts gets 400 before the 501")
  void missingEmailIsRefused() {
    ApiClient.assertProblem(api().get("/api/v1/accounts", validToken()), 400);
  }

  @Test
  @DisplayName("191 D9 an invalid or missing e-mail in GET /accounts gets 400 before the 501")
  void twoEmailValuesAreRefused() {
    var response = api().get("/api/v1/accounts?email=ana@farol.test&email=bob@farol.test", validToken());

    ApiClient.assertProblem(response, 400, "ana@farol.test", "bob@farol.test");
  }

  private static Stream<Arguments> malformedInput() {
    var user = UUID.randomUUID();
    return Stream.of(
        Arguments.of("POST", membersPath(), "{"),
        Arguments.of("POST", membersPath(), ""),
        Arguments.of("POST", membersPath(), "{\"userId\":\"" + user + "\"}"),
        Arguments.of("POST", membersPath(), "{\"userId\":\"" + user + "\",\"role\":\"RegularUser\"}"),
        Arguments.of("POST", membersPath(), "{\"userId\":\"not-a-uuid\",\"role\":\"EventAdmin\"}"),
        Arguments.of("GET", membersPath() + "?teamId=not-a-uuid", null),
        Arguments.of("GET", membersPath() + "?role=Bogus", null),
        Arguments.of("GET", membersPath() + "?role=EVENT_ADMIN", null));
  }

  @ParameterizedTest(name = "[{index}] {0} {1} {2}")
  @MethodSource("malformedInput")
  @DisplayName("191 D9 a malformed body or query value gets 400 before the use case")
  void malformedInputIsRefused(String method, String path, String body) {
    ApiClient.assertProblem(api().send(method, path, "Bearer " + validToken(), body), 400);
  }

  @Test
  @DisplayName("191 D9 a body that is not JSON gets 415 Problem Details")
  void bodyThatIsNotJsonIsRefused() {
    var response = post(membersPath(), "hello", "Content-Type", "text/plain");

    ApiClient.assertProblem(response, 415);
  }
}
