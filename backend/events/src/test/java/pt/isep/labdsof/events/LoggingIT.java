package pt.isep.labdsof.events;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import pt.isep.labdsof.events.domain.Membership.Role;
import pt.isep.labdsof.events.domain.Membership.Status;

/** Correlation id (spec AC-8) and what reaches the logs (M-10, D8), read from the structured console output. */
@ExtendWith(OutputCaptureExtension.class)
class LoggingIT extends IntegrationTest {

  private static final String HEADER = "X-Correlation-Id";
  private static final String REFUSAL_MESSAGE = "Refused access to an event (US-14)";

  private ApiClient api() {
    return new ApiClient(port);
  }

  private HttpResponse<String> get(String path, String token, String correlationId) {
    return api().send("GET", path, token == null ? null : "Bearer " + token, null,
        correlationId == null ? new String[0] : new String[] {HEADER, correlationId});
  }

  private static String code() {
    return "it-" + UUID.randomUUID().toString().substring(0, 8);
  }

  private static boolean accessLine(Map<String, Object> line, String correlationId, int status) {
    return correlationId.equals(line.get("correlationId")) && line.get("message") instanceof String message
        && message.contains(" -> " + status + " in ");
  }

  /** The access line is written after the response may have reached the client, so wait for it. */
  private static void awaitAccessLine(CapturedOutput output, String correlationId, int status) {
    await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
        assertThat(logLines(output)).anyMatch(line -> accessLine(line, correlationId, status)));
  }

  private static Stream<Arguments> correlationIds() {
    return Stream.of(Arguments.of("abcdefgh-1234-5678", true), Arguments.of(null, false),
        Arguments.of("abcd efgh", false), Arguments.of("abcdefg", false), Arguments.of("a".repeat(65), false));
  }

  @ParameterizedTest(name = "[{index}] {0}")
  @MethodSource("correlationIds")
  @DisplayName("192 AC-8 a valid X-Correlation-Id is returned as sent, any other gets a new UUID")
  void correlationIdIsKeptOrReplaced(String sent, boolean kept) {
    var response = get("/api/v1/status", null, sent);

    var returned = response.headers().firstValue(HEADER).orElseThrow();
    if (kept) {
      assertThat(returned).isEqualTo(sent);
    } else {
      assertThat(returned).isNotEqualTo(sent);
      assertThat(UUID.fromString(returned)).hasToString(returned);
    }
  }

  @Test
  @DisplayName("192 AC-8 a 401 of the security layer carries the correlation id")
  void securityRefusalCarriesTheCorrelationId() {
    var path = "/api/v1/events/" + UUID.randomUUID();

    var response = get(path, null, "from-the-client-401");

    assertThat(response.statusCode()).isEqualTo(401);
    assertThat(response.headers().allValues(HEADER)).containsExactly("from-the-client-401");
    var generated = get(path, null, null);
    assertThat(generated.statusCode()).isEqualTo(401);
    assertThat(generated.headers().firstValue(HEADER)).isPresent();
  }

  @Test
  @DisplayName("192 AC-8 every request is logged as logstash JSON carrying its correlation id")
  void requestsAreLoggedAsJsonWithTheCorrelationId(CapturedOutput output) {
    var admin = UUID.randomUUID();
    var event = insertEvent("Draft", null, null);
    member(event, admin, Role.EventAdmin, Status.Active, 1);
    var correlationId = "logged-" + UUID.randomUUID();

    get("/api/v1/events/" + event, JWKS.valid(admin.toString()), correlationId);

    awaitAccessLine(output, correlationId, 200);
    var mine = logLines(output).stream().filter(line -> correlationId.equals(line.get("correlationId"))).toList();
    assertThat(mine).allSatisfy(line -> assertThat(line).containsKeys("@timestamp", "level", "logger_name",
        "message"));
    assertThat(mine).anyMatch(line -> line.get("message") instanceof String message
        && message.startsWith("GET /api/v1/events/" + event + " -> 200 in "));
  }

  private UUID published() {
    return insertEvent("Published", null, code());
  }

  private static Stream<Arguments> refusals() {
    return Stream.of(
        Arguments.of("unknown event", "NO_SUCH_EVENT", 0),
        Arguments.of("non-member", "NOT_A_MEMBER", 1),
        Arguments.of("member of another event", "NOT_A_MEMBER", 2),
        Arguments.of("removed member", "NOT_A_MEMBER", 3),
        Arguments.of("FarolAdmin non-member", "NOT_A_MEMBER", 4),
        Arguments.of("RegularUser on a Draft", "NOT_VISIBLE", 5));
  }

  @ParameterizedTest(name = "[{index}] {0}")
  @MethodSource("refusals")
  @DisplayName("192 D8 a refused attempt over HTTP is logged at WARN with the user, the event, the time and the "
      + "correlation id")
  void refusedAttemptIsLogged(String name, String refusal, int scenario, CapturedOutput output) {
    var user = UUID.randomUUID();
    final var asked = scenario == 0 ? UUID.randomUUID() : scenario == 5 ? insertEvent("Draft", null, null)
        : published();
    switch (scenario) {
      case 2 -> member(published(), user, Role.EventAdmin, Status.Active, 1);
      case 3 -> {
        member(asked, user, Role.RegularUser, Status.Active, 1);
        member(asked, user, Role.RegularUser, Status.Removed, 2);
      }
      case 4 -> farolAdmin(output, user);
      case 5 -> member(asked, user, Role.RegularUser, Status.Active, 1);
      default -> { }
    }
    var correlationId = "refused-" + UUID.randomUUID();

    var response = get("/api/v1/events/" + asked, JWKS.valid(user.toString()), correlationId);

    assertThat(response.statusCode()).isEqualTo(404);
    awaitAccessLine(output, correlationId, 404);
    var refusals = logLines(output).stream()
        .filter(line -> REFUSAL_MESSAGE.equals(line.get("message")) && user.toString().equals(line.get("userId")))
        .toList();
    assertThat(refusals).singleElement().satisfies(line -> {
      assertThat(line.get("level")).isEqualTo("WARN");
      assertThat(line.get("logger_name")).asString().endsWith("LoggingAccessAudit");
      assertThat(line.get("eventId")).isEqualTo(asked.toString());
      assertThat(Instant.parse((String) line.get("at"))).isNotNull();
      assertThat(line.get("refusal")).isEqualTo(refusal);
      assertThat(line.get("correlationId")).isEqualTo(response.headers().firstValue(HEADER).orElseThrow());
    });
  }

  private void farolAdmin(CapturedOutput output, UUID user) {
    var messageId = UUID.randomUUID();
    var payload = "{\"userId\":\"%s\",\"role\":\"FarolAdmin\",\"granted\":true,\"version\":1}".formatted(user);
    var properties = new MessageProperties();
    properties.setMessageId(messageId.toString());
    properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
    Infrastructure.adminTemplate().send("farol.auth-users", "global-role.changed", new Message(
        LocalCopiesIT.envelope(messageId, "GlobalRoleChanged", null, payload).getBytes(UTF_8), properties));
    await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(logLines(output)).anyMatch(line ->
        messageId.toString().equals(line.get("messageId")) && "GlobalRoleChanged".equals(line.get("type"))));
  }

  @Test
  @DisplayName("192 D8 a granted read is not logged as a refusal")
  void grantedReadIsNotARefusal(CapturedOutput output) {
    var admin = UUID.randomUUID();
    var event = insertEvent("Draft", null, null);
    member(event, admin, Role.EventAdmin, Status.Active, 1);
    var correlationId = "granted-" + UUID.randomUUID();

    get("/api/v1/events/" + event, JWKS.valid(admin.toString()), correlationId);

    awaitAccessLine(output, correlationId, 200);
    assertThat(logLines(output)).noneMatch(line -> REFUSAL_MESSAGE.equals(line.get("message"))
        && admin.toString().equals(line.get("userId")));
  }

  private String awaitedRequest(CapturedOutput output, String method, String path, String token, String body,
      int status, String... headers) {
    var correlationId = "leak-" + UUID.randomUUID();
    var all = new String[headers.length + 2];
    all[0] = HEADER;
    all[1] = correlationId;
    System.arraycopy(headers, 0, all, 2, headers.length);
    api().send(method, path, token == null ? null : "Bearer " + token, body, all);
    awaitAccessLine(output, correlationId, status);
    return correlationId;
  }

  @Test
  @DisplayName("192 AC-8 no e-mail, name or token reaches the logs")
  void noPersonalDataReachesTheLogs(CapturedOutput output) {
    var id = UUID.randomUUID();
    var email = "leak-" + id + "@farol.test";
    var name = "Leaky Name " + id;
    var user = UUID.randomUUID();
    var valid = JWKS.sign(JWKS.claims(user.toString()).claim("email", email).claim("name", name).build());
    var expired = JWKS.sign(JWKS.claims(UUID.randomUUID().toString()).claim("email", email).claim("name", name)
        .expirationTime(java.util.Date.from(Instant.now().minusSeconds(120))).build());
    var tampered = Jwks.tampered(JWKS.sign(JWKS.claims(UUID.randomUUID().toString()).claim("email", email)
        .claim("name", name).build()));
    var event = insertEvent("Draft", null, null);
    member(event, user, Role.EventAdmin, Status.Active, 1);
    var path = "/api/v1/events/" + event;
    var secondEvent = "/api/v1/events/" + UUID.randomUUID();

    awaitedRequest(output, "GET", path, valid, null, 200);
    awaitedRequest(output, "GET", path, expired, null, 401);
    awaitedRequest(output, "GET", path, tampered, null, 401);
    awaitedRequest(output, "GET", secondEvent, valid, null, 404);
    awaitedRequest(output, "POST", "/api/v1/events", valid,
        "{\"name\":\"Queima\",\"organiserName\":\"FAP\",\"dates\":{\"start\":\"2027-05-02\","
            + "\"end\":\"2027-05-09\"},\"note\":\"" + email + "\"}", 400, "Content-Type", "application/json");
    awaitedRequest(output, "POST", "/api/v1/events", valid, "hello", 415, "Content-Type", "text/plain");
    awaitedRequest(output, "POST", "/api/v1/events", valid,
        "{\"name\":\"Queima\",\"organiserName\":\"" + name + "\",\"dates\":{\"start\":\"2027-05-02\","
            + "\"end\":\"2027-05-09\"}}", 501, "Content-Type", "application/json");

    var logged = output.getAll();
    for (var secret : List.of(email, name, valid, expired, tampered, "Leaky Name", valid.split("\\.")[1])) {
      assertThat(logged).doesNotContain(secret);
    }
  }

  @Test
  @DisplayName("192 AC-8 a rejected correlation id is not logged")
  void rejectedCorrelationIdIsNotLogged(CapturedOutput output) {
    var rejected = "reject me " + UUID.randomUUID();

    var response = get("/api/v1/status", null, rejected);

    var replaced = response.headers().firstValue(HEADER).orElseThrow();
    awaitAccessLine(output, replaced, 200);
    assertThat(output.getAll()).doesNotContain(rejected).doesNotContain(rejected.substring(10));
  }
}
