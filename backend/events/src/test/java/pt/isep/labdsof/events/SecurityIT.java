package pt.isep.labdsof.events;

import static org.assertj.core.api.Assertions.assertThat;

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
import pt.isep.labdsof.events.domain.Membership.Role;
import pt.isep.labdsof.events.domain.Membership.Status;

/** Authentication of every request and the 400/415 answers of the web layer, over real HTTP with signed tokens. */
class SecurityIT extends IntegrationTest {

  private static final String JSON_TYPE = "application/json";

  private ApiClient api() {
    return new ApiClient(port);
  }

  private static String user() {
    return UUID.randomUUID().toString();
  }

  /** A 401 Problem Details with a Bearer challenge that repeats none of the given texts. */
  static void assertRefused(HttpResponse<String> response, String... mustNotContain) {
    ApiClient.assertProblem(response, 401, mustNotContain);
    assertThat(response.headers().firstValue("WWW-Authenticate")).hasValueSatisfying(
        challenge -> assertThat(challenge).startsWith("Bearer"));
  }

  private static String eventPath(UUID eventId) {
    return "/api/v1/events/" + eventId;
  }

  @Test
  @DisplayName("192 AC-2 a request without a token gets 401 Problem Details with WWW-Authenticate Bearer")
  void noTokenIsRefused() {
    assertRefused(api().get(eventPath(UUID.randomUUID()), null));
  }

  @Test
  @DisplayName("192 AC-2 a token expired more than 60 s ago gets 401")
  void expiredTokenIsRefused() {
    var token = JWKS.expired(user(), Duration.ofSeconds(120));

    assertRefused(api().get(eventPath(UUID.randomUUID()), token), token);
  }

  @Test
  @DisplayName("192 AC-2 a token expired less than 60 s ago is still accepted (clock skew)")
  void slightlyExpiredTokenIsAccepted() {
    var event = insertEvent("Published", null, "it-" + UUID.randomUUID());
    var admin = UUID.randomUUID();
    member(event, admin, Role.EventAdmin, Status.Active, 1);

    var response = api().get(eventPath(event), JWKS.expired(admin.toString(), Duration.ofSeconds(30)));

    assertThat(response.statusCode()).isEqualTo(200);
  }

  @Test
  @DisplayName("192 AC-2 a token whose payload was changed after signing gets 401")
  void tamperedTokenIsRefused() {
    var token = Jwks.tampered(JWKS.valid(user()));

    assertRefused(api().get(eventPath(UUID.randomUUID()), token), token);
  }

  @Test
  @DisplayName("192 AC-2 a token signed by another key under the same key id gets 401")
  void wrongSignatureIsRefused() {
    var token = JWKS.wrongSignature(user());

    assertRefused(api().get(eventPath(UUID.randomUUID()), token), token);
  }

  @Test
  @DisplayName("192 AC-2 a token signed by an unknown key gets 401")
  void unknownKeyIsRefused() {
    var token = JWKS.unknownKey(user());

    assertRefused(api().get(eventPath(UUID.randomUUID()), token), token);
  }

  @Test
  @DisplayName("192 AC-2 a token from another issuer gets 401")
  void wrongIssuerIsRefused() {
    var token = JWKS.wrongIssuer(user());

    assertRefused(api().get(eventPath(UUID.randomUUID()), token), token);
  }

  @Test
  @DisplayName("192 AC-2 a token for another audience gets 401")
  void wrongAudienceIsRefused() {
    var token = JWKS.wrongAudience(user());

    assertRefused(api().get(eventPath(UUID.randomUUID()), token), token);
  }

  @Test
  @DisplayName("192 AC-2 an unsigned token (alg none) gets 401")
  void unsignedTokenIsRefused() {
    var token = JWKS.unsigned(user());

    assertRefused(api().get(eventPath(UUID.randomUUID()), token), token);
  }

  @Test
  @DisplayName("192 AC-2 a token signed with HS256 instead of RS256 gets 401")
  void hmacTokenIsRefused() {
    var token = JWKS.hmac(user());

    assertRefused(api().get(eventPath(UUID.randomUUID()), token), token);
  }

  @ParameterizedTest(name = "[{index}] {0}")
  @ValueSource(strings = {"Bearer", "Bearer not a jwt", "Bearer a.b.c", "Bearer not-a-jwt",
      "Basic dXNlcjpwYXNz", "Token {valid}"})
  @DisplayName("192 AC-2 a malformed Authorization header gets 401")
  void malformedHeaderIsRefused(String header) {
    var valid = JWKS.valid(user());

    assertRefused(api().send("GET", eventPath(UUID.randomUUID()), header.replace("{valid}", valid)), valid);
  }

  private static Stream<String> badSubjects() {
    return Stream.of("not-a-uuid", "1-1-1-1-1", "UPPER", "", "ABSENT");
  }

  @ParameterizedTest(name = "[{index}] sub {0}")
  @MethodSource("badSubjects")
  @DisplayName("192 AC-2 a token whose sub is not a canonical UUID gets 401")
  void subThatIsNotACanonicalUuidIsRefused(String subject) {
    var admin = UUID.randomUUID();
    var event = insertEvent("Published", null, "it-" + UUID.randomUUID());
    member(event, admin, Role.EventAdmin, Status.Active, 1);
    var claims = JWKS.claims(subject.equals("UPPER") ? admin.toString().toUpperCase() : subject);
    if (subject.equals("ABSENT")) {
      claims = JWKS.claims("placeholder").subject(null);
    }
    var token = JWKS.sign(claims.build());

    assertRefused(api().get(eventPath(event), token), token);
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

  @ParameterizedTest(name = "[{index}] {0} with {1}")
  @MethodSource("securedOperationsWithBadTokens")
  @DisplayName("192 AC-2 every secured operation of the contract refuses a missing or invalid token with 401 "
      + "Problem Details")
  void everySecuredOperationRefusesBadTokens(String operation, TokenKind kind) {
    var sample = ContractIT.sampleRequest(operation);
    var token = kind.token().apply(user());
    var headers = sample.contentType() == null ? new String[0] : new String[] {"Content-Type", sample.contentType()};

    var response = api().send(sample.method(), sample.path(), token == null ? null : "Bearer " + token,
        sample.body(), headers);

    assertRefused(response, token == null ? new String[0] : new String[] {token});
  }

  @Test
  @DisplayName("192 AC-2 GET /api/v1/status is public for the health check")
  void statusIsPublic() {
    var response = api().get("/api/v1/status", null);

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).isEqualTo("{\"service\":\"events\",\"status\":\"ok\"}");
  }

  @Test
  @DisplayName("192 AC-2 the actuator health endpoint needs a token")
  void actuatorHealthNeedsAToken() {
    assertRefused(api().get("/actuator/health", null));
  }

  private HttpResponse<String> send(String method, String path, String body, String contentType) {
    var headers = contentType == null ? new String[0] : new String[] {"Content-Type", contentType};
    return api().send(method, path, "Bearer " + JWKS.valid(user()), body, headers);
  }

  private static String base(String suffix) {
    return "/api/v1/events/" + UUID.randomUUID() + suffix;
  }

  private static final String POI = "/map-versions/1/points-of-interest";
  private static final String NEW_EVENT = "{\"name\":\"Queima\",\"organiserName\":\"FAP\","
      + "\"dates\":{\"start\":\"2027-05-02\",\"end\":\"2027-05-09\"}";
  private static final String NEW_POI = "{\"type\":\"Stage\",\"name\":\"Main stage\","
      + "\"position\":{\"x\":0.25,\"y\":0.75}";

  private static Stream<Arguments> unknownFields() {
    return Stream.of(
        Arguments.of("POST", "/api/v1/events", NEW_EVENT + ",\"status\":\"Published\"}"),
        Arguments.of("POST", "/api/v1/events", NEW_EVENT + ",\"eventId\":\"" + UUID.randomUUID() + "\"}"),
        Arguments.of("POST", "/api/v1/events", NEW_EVENT.replace("\"2027-05-09\"}", "\"2027-05-09\",\"timezone\":\"UTC\"}")
            + "}"),
        Arguments.of("POST", base("/teams"), "{\"name\":\"Medical\",\"teamId\":\"" + UUID.randomUUID() + "\"}"),
        Arguments.of("PATCH", base("/teams/" + UUID.randomUUID()),
            "{\"name\":\"Medical\",\"handledCategories\":[],\"permissions\":[]}"),
        Arguments.of("PUT", base("/teams/" + UUID.randomUUID() + "/permissions"),
            "[{\"permission\":\"Map.Edit\",\"teamId\":\"" + UUID.randomUUID() + "\"}]"),
        Arguments.of("POST", base(POI), NEW_POI + ",\"poiId\":\"" + UUID.randomUUID() + "\"}"),
        Arguments.of("PATCH", base(POI + "/" + UUID.randomUUID()), "{\"name\":\"Main\",\"type\":\"Wc\"}"));
  }

  @ParameterizedTest(name = "[{index}] {0} {1}")
  @MethodSource("unknownFields")
  @DisplayName("192 D6 an unknown JSON field gets 400 before the 501 (mass assignment)")
  void unknownJsonFieldIsRefused(String method, String path, String body) {
    ApiClient.assertProblem(send(method, path, body, JSON_TYPE), 400);
  }

  private static Stream<Arguments> malformedPaths() {
    var bad = "not-a-uuid";
    var id = UUID.randomUUID();
    var event = "/api/v1/events/" + id;
    var team = "/teams/" + UUID.randomUUID();
    var poi = POI + "/" + UUID.randomUUID();
    var teamBody = "{\"name\":\"Medical\",\"handledCategories\":[]}";
    var permissions = "[{\"permission\":\"Map.Edit\"}]";
    var multipart = ContractIT.sampleRequest("POST /api/v1/events/{eventId}/map-versions");
    return Stream.of(
        Arguments.of("POST", "/api/v1/events/" + bad + "/publish", null, null),
        Arguments.of("GET", "/api/v1/events/" + bad + "/teams", null, null),
        Arguments.of("POST", "/api/v1/events/" + bad + "/teams", "{\"name\":\"Medical\"}", JSON_TYPE),
        Arguments.of("PATCH", "/api/v1/events/" + bad + team, teamBody, JSON_TYPE),
        Arguments.of("DELETE", "/api/v1/events/" + bad + team, null, null),
        Arguments.of("PUT", "/api/v1/events/" + bad + team + "/permissions", permissions, JSON_TYPE),
        Arguments.of("POST", "/api/v1/events/" + bad + "/map-versions", multipart.body(), multipart.contentType()),
        Arguments.of("GET", "/api/v1/events/" + bad + "/map-versions/current", null, null),
        Arguments.of("GET", "/api/v1/events/" + bad + "/map-versions/1", null, null),
        Arguments.of("GET", "/api/v1/events/" + bad + "/map-versions/1/image", null, null),
        Arguments.of("POST", "/api/v1/events/" + bad + POI, NEW_POI + "}", JSON_TYPE),
        Arguments.of("PATCH", "/api/v1/events/" + bad + poi, "{\"name\":\"Main\"}", JSON_TYPE),
        Arguments.of("DELETE", "/api/v1/events/" + bad + poi, null, null),
        Arguments.of("PATCH", event + "/teams/" + bad, teamBody, JSON_TYPE),
        Arguments.of("DELETE", event + "/teams/" + bad, null, null),
        Arguments.of("PUT", event + "/teams/" + bad + "/permissions", permissions, JSON_TYPE),
        Arguments.of("PATCH", event + POI + "/" + bad, "{\"name\":\"Main\"}", JSON_TYPE),
        Arguments.of("DELETE", event + POI + "/" + bad, null, null));
  }

  @ParameterizedTest(name = "[{index}] {0} {1}")
  @MethodSource("malformedPaths")
  @DisplayName("192 D6 a malformed UUID in a path gets 400 before the 501")
  void malformedPathUuidIsRefused(String method, String path, String body, String contentType) {
    ApiClient.assertProblem(send(method, path, body, contentType), 400);
  }

  private static Stream<Arguments> malformedVersions() {
    var versions = List.of("abc", "0", "-1", "2147483648");
    var operations = List.of(
        new String[] {"GET", "/map-versions/%s", null},
        new String[] {"GET", "/map-versions/%s/image", null},
        new String[] {"POST", "/map-versions/%s/points-of-interest", NEW_POI + "}"},
        new String[] {"PATCH", "/map-versions/%s/points-of-interest/" + UUID.randomUUID(), "{\"name\":\"Main\"}"},
        new String[] {"DELETE", "/map-versions/%s/points-of-interest/" + UUID.randomUUID(), null});
    return versions.stream().flatMap(version -> operations.stream().map(operation -> Arguments.of(operation[0],
        base(operation[1].formatted(version)), operation[2])));
  }

  @ParameterizedTest(name = "[{index}] {0} {1}")
  @MethodSource("malformedVersions")
  @DisplayName("192 D6 a malformed or non-positive map version gets 400 before the 501")
  void malformedVersionIsRefused(String method, String path, String body) {
    ApiClient.assertProblem(send(method, path, body, body == null ? null : JSON_TYPE), 400);
  }

  private static String event(String replaceFrom, String replaceWith) {
    return (NEW_EVENT + "}").replace(replaceFrom, replaceWith);
  }

  private static Stream<Arguments> malformedBodies() {
    var team = "/teams/" + UUID.randomUUID();
    var item = "{\"permission\":\"Map.Edit\"}";
    return Stream.of(
        Arguments.of("POST", "/api/v1/events", "{"),
        Arguments.of("POST", "/api/v1/events", ""),
        Arguments.of("POST", "/api/v1/events", event("\"Queima\"", "\"\"")),
        Arguments.of("POST", "/api/v1/events", event("\"Queima\"", "\"" + "n".repeat(101) + "\"")),
        Arguments.of("POST", "/api/v1/events", event("\"organiserName\":\"FAP\",", "")),
        Arguments.of("POST", "/api/v1/events", event(",\"dates\":{\"start\":\"2027-05-02\",\"end\":\"2027-05-09\"}", "")),
        Arguments.of("POST", "/api/v1/events", event(",\"end\":\"2027-05-09\"", "")),
        Arguments.of("POST", "/api/v1/events", event("2027-05-02", "2027-13-01")),
        Arguments.of("POST", "/api/v1/events", NEW_EVENT + ",\"description\":\"" + "d".repeat(2001) + "\"}"),
        Arguments.of("POST", base("/teams"), "{\"name\":\"\"}"),
        Arguments.of("PATCH", base(team), "{\"name\":\"Medical\",\"handledCategories\":[\"Bogus\"]}"),
        Arguments.of("PUT", base(team + "/permissions"), "[" + String.join(",", java.util.Collections.nCopies(9, item)) + "]"),
        Arguments.of("PUT", base(team + "/permissions"), "[{\"permission\":\"Bogus\"}]"),
        Arguments.of("PUT", base(team + "/permissions"), "[{\"permission\":\"REPORTS_REVIEW\"}]"),
        Arguments.of("PUT", base(team + "/permissions"), "[{\"permission\":\"Map.Edit\",\"reportTypes\":[\"Bogus\"]}]"),
        Arguments.of("PUT", base(team + "/permissions"), "[{}]"),
        Arguments.of("POST", base(POI), NEW_POI.replace("Stage", "Bogus") + "}"),
        Arguments.of("POST", base(POI), NEW_POI.replace("0.25", "1.5") + "}"),
        Arguments.of("POST", base(POI), NEW_POI.replace("0.75", "-0.1") + "}"),
        Arguments.of("POST", base(POI), "{\"type\":\"Stage\",\"name\":\"Main stage\"}"),
        Arguments.of("POST", base(POI), NEW_POI.replace("Main stage", "n".repeat(101)) + "}"),
        Arguments.of("PATCH", base(POI + "/" + UUID.randomUUID()), "{\"position\":{\"x\":2,\"y\":0.5}}"));
  }

  @ParameterizedTest(name = "[{index}] {0} {1}")
  @MethodSource("malformedBodies")
  @DisplayName("192 D6 a malformed or out-of-range body gets 400 before the 501")
  void malformedBodyIsRefused(String method, String path, String body) {
    ApiClient.assertProblem(send(method, path, body, JSON_TYPE), 400);
  }

  private static Stream<Arguments> wrongMediaTypes() {
    return Stream.of(
        Arguments.of("POST", "/api/v1/events", "hello", "text/plain"),
        Arguments.of("PATCH", base("/teams/" + UUID.randomUUID()), "<a/>", "application/xml"),
        Arguments.of("POST", base("/map-versions"), "{}", JSON_TYPE));
  }

  @ParameterizedTest(name = "[{index}] {0} {1} as {3}")
  @MethodSource("wrongMediaTypes")
  @DisplayName("192 D6 a body of another media type gets 415 Problem Details")
  void bodyOfAnotherMediaTypeIsRefused(String method, String path, String body, String contentType) {
    ApiClient.assertProblem(send(method, path, body, contentType), 415);
  }

  @Test
  @DisplayName("192 D6 a map upload without its file part gets 400 before the 501")
  void uploadWithoutFileIsRefused() {
    var body = "--farol-boundary\r\nContent-Disposition: form-data; name=\"other\"\r\n\r\nx\r\n--farol-boundary--\r\n";

    ApiClient.assertProblem(send("POST", base("/map-versions"), body, "multipart/form-data; boundary=farol-boundary"),
        400);
  }
}
