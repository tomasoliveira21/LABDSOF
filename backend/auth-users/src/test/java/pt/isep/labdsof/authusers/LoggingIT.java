package pt.isep.labdsof.authusers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
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
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import tools.jackson.databind.json.JsonMapper;

/** Correlation id (spec D8) and what reaches the logs (M-10), read from the structured console output. */
@ExtendWith(OutputCaptureExtension.class)
class LoggingIT extends IntegrationTest {

  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final String HEADER = "X-Correlation-Id";

  private ApiClient api() {
    return new ApiClient(port);
  }

  private HttpResponse<String> get(String path, String token, String correlationId) {
    return api().send("GET", path, token == null ? null : "Bearer " + token, null,
        correlationId == null ? new String[0] : new String[] {HEADER, correlationId});
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
  @DisplayName("191 AC-8 a valid X-Correlation-Id is returned as sent, any other gets a new UUID")
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
  @DisplayName("191 AC-8 a 401 of the security layer carries the correlation id")
  void securityRefusalCarriesTheCorrelationId() {
    var response = get("/api/v1/me", null, "from-the-client-401");

    assertThat(response.statusCode()).isEqualTo(401);
    assertThat(response.headers().allValues(HEADER)).containsExactly("from-the-client-401");
    var generated = get("/api/v1/me", null, null);
    assertThat(generated.statusCode()).isEqualTo(401);
    assertThat(generated.headers().firstValue(HEADER)).isPresent();
  }

  @Test
  @DisplayName("191 AC-8 the outbox row of a first GET /api/v1/me keeps the request's correlation id")
  void outboxRowKeepsTheCorrelationId() {
    var user = UUID.randomUUID();
    var correlationId = "outbox-" + UUID.randomUUID();

    var response = get("/api/v1/me", JWKS.valid(user.toString()), correlationId);

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(jdbc.sql("SELECT correlation_id FROM outbox WHERE body->'payload'->>'userId' = :id")
        .param("id", user.toString()).query(String.class).list()).containsExactly(correlationId);
  }

  @Test
  @DisplayName("191 AC-8 every request is logged as logstash JSON carrying its correlation id")
  void requestsAreLoggedAsJsonWithTheCorrelationId(CapturedOutput output) {
    var user = UUID.randomUUID();
    var correlationId = "logged-" + UUID.randomUUID();

    get("/api/v1/me", JWKS.valid(user.toString()), correlationId);

    awaitAccessLine(output, correlationId, 200);
    var mine = logLines(output).stream().filter(line -> correlationId.equals(line.get("correlationId"))).toList();
    assertThat(mine).allSatisfy(line -> assertThat(line).containsKeys("@timestamp", "level", "logger_name",
        "message"));
    assertThat(mine).anyMatch(line -> line.get("message") instanceof String message
        && message.startsWith("GET /api/v1/me -> 200 in "));
    assertThat(mine).anyMatch(line -> ("GET /me by userId=" + user).equals(line.get("message")));
  }

  private String awaitedRequest(CapturedOutput output, String method, String path, String token, String body,
      int status) {
    var correlationId = "leak-" + UUID.randomUUID();
    api().send(method, path, token == null ? null : "Bearer " + token, body, HEADER, correlationId);
    awaitAccessLine(output, correlationId, status);
    return correlationId;
  }

  @Test
  @DisplayName("191 AC-8 no e-mail, name or token reaches the logs")
  void noPersonalDataReachesTheLogs(CapturedOutput output) {
    var id = UUID.randomUUID();
    var email = "leak-" + id + "@farol.test";
    var name = "Leaky Name " + id;
    var user = UUID.randomUUID();
    var valid = JWKS.sign(JWKS.claims(user.toString()).claim("email", email).claim("name", name).build());
    var expired = JWKS.expired(UUID.randomUUID().toString(), Duration.ofSeconds(120));
    var tampered = Jwks.tampered(JWKS.valid(UUID.randomUUID().toString()));
    var disabled = UUID.randomUUID();
    jdbc.sql("""
        INSERT INTO user_account (user_id, email, email_verified, display_name, status, version, created_at,
            updated_at)
        VALUES (:id, :email, true, :name, 'Disabled', 1, '2026-01-01T00:00:00Z', '2026-01-01T00:00:00Z')
        """).param("id", disabled).param("email", email.replace("leak-", "disabled-")).param("name", name).update();
    var disabledToken = JWKS.sign(JWKS.claims(disabled.toString()).claim("email", email).claim("name", name).build());
    var badEmail = URLEncoder.encode("not-" + email.replace("@", "-at-"), StandardCharsets.UTF_8);
    var goodEmail = URLEncoder.encode(email, StandardCharsets.UTF_8);
    var unknownField = "{\"userId\":\"" + user + "\",\"role\":\"EventAdmin\",\"note\":\"" + email + "\"}";
    var members = "/api/v1/events/" + UUID.randomUUID() + "/members";

    awaitedRequest(output, "GET", "/api/v1/me", valid, null, 200);
    awaitedRequest(output, "GET", "/api/v1/me", expired, null, 401);
    awaitedRequest(output, "GET", "/api/v1/me", tampered, null, 401);
    awaitedRequest(output, "GET", "/api/v1/me", disabledToken, null, 403);
    awaitedRequest(output, "GET", "/api/v1/accounts?email=" + badEmail, valid, null, 400);
    awaitedRequest(output, "POST", members, valid, unknownField, 400);
    // GET /me above created this caller's account with that very e-mail, so making them a FarolAdmin lets the
    // lookup below really find it: the no-personal-data assertion then runs over a hit, not over a refusal
    jdbc.sql("INSERT INTO global_role_grant (user_id, role, granted_at, version) "
        + "VALUES (:u, 'FarolAdmin', now(), 1)").param("u", user).update();
    awaitedRequest(output, "GET", "/api/v1/accounts?email=" + goodEmail, valid, null, 200);

    var logged = output.getAll();
    for (var secret : List.of(email, name, valid, expired, tampered, disabledToken, "Leaky Name",
        email.replace("leak-", "disabled-"), valid.split("\\.")[1])) {
      assertThat(logged).doesNotContain(secret);
    }
  }

  @Test
  @DisplayName("US-18 AC-1 the team assignment is recorded with who made it and when, and never with a name")
  void theTeamAssignmentIsRecordedWithoutAnyName(CapturedOutput output) {
    var sofia = UUID.randomUUID();
    var rita = UUID.randomUUID();
    var event = UUID.randomUUID();
    var team = UUID.randomUUID();
    seedAccount(sofia, "Sofia Logged");
    seedMembership(event, sofia, "EventAdmin");
    seedAccount(rita, "Rita Logged");
    seedMembership(event, rita, "RegularUser");
    seedTeam(event, team, false);
    var path = "/api/v1/events/" + event + "/members/" + rita + "/teams/" + team;
    var correlationId = "assign-" + UUID.randomUUID();

    api().send("PUT", path, "Bearer " + JWKS.valid(sofia.toString()), null, HEADER, correlationId);
    api().send("DELETE", path, "Bearer " + JWKS.valid(sofia.toString()), null, HEADER, correlationId);
    awaitAccessLine(output, correlationId, 204);

    var mine = logLines(output).stream().filter(line -> correlationId.equals(line.get("correlationId"))).toList();
    assertThat(mine).anyMatch(line -> "Added a member to a team (US-18)".equals(line.get("message"))
        && sofia.toString().equals(line.get("actorUserId")) && rita.toString().equals(line.get("userId"))
        && team.toString().equals(line.get("teamId")) && event.toString().equals(line.get("eventId")));
    assertThat(mine).anyMatch(line -> "Removed a member from a team (US-18)".equals(line.get("message")));
    assertThat(output.getAll()).doesNotContain("Sofia Logged").doesNotContain("Rita Logged");
  }

  @Test
  @DisplayName("US-12 AC-1 the join is recorded with who joined and when, and never with a name")
  void theJoinIsRecordedWithoutAnyName(CapturedOutput output) {
    var rita = UUID.randomUUID();
    var event = UUID.randomUUID();
    seedAccount(rita, "Rita Joined");
    jdbc.sql("INSERT INTO event_copy (event_id, name, status, version) VALUES (:id, 'Queima', 'Published', 1)")
        .param("id", event).update();
    var correlationId = "join-" + UUID.randomUUID();

    api().send("POST", "/api/v1/events/" + event + "/members/me", "Bearer " + JWKS.valid(rita.toString()), null,
        HEADER, correlationId);
    awaitAccessLine(output, correlationId, 201);

    assertThat(logLines(output)).anyMatch(line -> correlationId.equals(line.get("correlationId"))
        && "Joined an event by its link (US-12)".equals(line.get("message"))
        && rita.toString().equals(line.get("userId")) && event.toString().equals(line.get("eventId")));
    assertThat(output.getAll()).doesNotContain("Rita Joined");
  }

  @Test
  @DisplayName("US-14 a refused call about an event's members is recorded with the reason")
  void aRefusedCallAboutMembersIsRecorded(CapturedOutput output) {
    var stranger = UUID.randomUUID();
    var event = UUID.randomUUID();
    var correlationId = awaitedRequest(output, "GET", "/api/v1/events/" + event + "/members",
        JWKS.valid(stranger.toString()), null, 404);

    assertThat(logLines(output)).anyMatch(line -> correlationId.equals(line.get("correlationId"))
        && "Refused a call about an event's members (US-14)".equals(line.get("message"))
        && "NOT_A_MEMBER".equals(line.get("refusal")) && event.toString().equals(line.get("eventId")));
  }

  @Test
  @DisplayName("191 AC-8 a rejected correlation id is not logged")
  void rejectedCorrelationIdIsNotLogged(CapturedOutput output) {
    var rejected = "reject me " + UUID.randomUUID();

    // Not /api/v1/status: the health check is logged at DEBUG, so it writes no access line to wait for
    var response = get("/api/v1/me", null, rejected);

    var replaced = response.headers().firstValue(HEADER).orElseThrow();
    awaitAccessLine(output, replaced, 401);
    assertThat(output.getAll()).doesNotContain(rejected).doesNotContain(rejected.substring(10));
  }
}
