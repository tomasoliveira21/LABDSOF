package pt.isep.labdsof.reports;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.TestPropertySource;
import pt.isep.labdsof.reports.domain.Category;
import pt.isep.labdsof.reports.domain.Membership.Role;
import pt.isep.labdsof.reports.domain.Permission;
import pt.isep.labdsof.reports.domain.Team;

/** AC-4: the JWT matrix and per-event isolation on every implemented endpoint (spec §5). */
@ExtendWith(OutputCaptureExtension.class)
@TestPropertySource(properties = "logging.structured.format.console=logstash")
class SecurityIT extends IntegrationTest {

  final UUID event = UUID.randomUUID();
  final String user = "user-" + UUID.randomUUID();

  String path() {
    return "/api/v1/events/" + event + "/help-requests";
  }

  @Test
  @DisplayName("194 AC-4 a request without a token gets 401")
  void noToken() throws Exception {
    assertThat(post(path(), null, submitBody(UUID.randomUUID(), "help")).statusCode()).isEqualTo(401);
    assertThat(get(path() + "/" + UUID.randomUUID(), null).statusCode()).isEqualTo(401);
  }

  @Test
  @DisplayName("194 AC-4 an expired token gets 401")
  void expired() throws Exception {
    member(event, user, Role.RegularUser);
    assertThat(post(path(), JWKS.expired(user), submitBody(UUID.randomUUID(), "help")).statusCode())
        .isEqualTo(401);
  }

  @Test
  @DisplayName("194 AC-4 a token with a wrong signature gets 401")
  void wrongSignature() throws Exception {
    member(event, user, Role.RegularUser);
    assertThat(post(path(), JWKS.wrongSignature(user), submitBody(UUID.randomUUID(), "help"))
        .statusCode()).isEqualTo(401);
  }

  @Test
  @DisplayName("194 AC-4 a token from another issuer gets 401")
  void wrongIssuer() throws Exception {
    member(event, user, Role.RegularUser);
    assertThat(post(path(), JWKS.wrongIssuer(user), submitBody(UUID.randomUUID(), "help")).statusCode())
        .isEqualTo(401);
  }

  @Test
  @DisplayName("194 AC-4 a token for another audience gets 401")
  void wrongAudience() throws Exception {
    member(event, user, Role.RegularUser);
    assertThat(post(path(), JWKS.wrongAudience(user), submitBody(UUID.randomUUID(), "help"))
        .statusCode()).isEqualTo(401);
  }

  @Test
  @DisplayName("194 AC-4 a malformed token gets 401")
  void malformedToken() throws Exception {
    assertThat(post(path(), "not-a-jwt", submitBody(UUID.randomUUID(), "help")).statusCode())
        .isEqualTo(401);
  }

  @Test
  @DisplayName("194 AC-4 a valid token of a member submits a request and reads it back")
  void validToken() throws Exception {
    member(event, user, Role.RegularUser);
    var created = post(path(), JWKS.valid(user), submitBody(UUID.randomUUID(), "Friend fainted"));
    assertThat(created.statusCode()).isEqualTo(201);
    String id = JsonPath.read(created.body(), "$.id");
    assertThat(created.headers().firstValue("Location")).contains(path() + "/" + id);
    assertThat(JsonPath.<String>read(created.body(), "$.status")).isEqualTo("Submitted");

    var read = get(path() + "/" + id, JWKS.valid(user));
    assertThat(read.statusCode()).isEqualTo(200);
    assertThat(read.body()).isEqualTo(created.body());
    assertThat(JsonPath.<java.util.Map<String, Object>>read(read.body(), "$"))
        .containsOnlyKeys("id", "description", "status", "updatedAt");
  }

  @Test
  @DisplayName("194 AC-2 a retried submit returns 200 with the request already saved")
  void retriedSubmit() throws Exception {
    member(event, user, Role.RegularUser);
    var body = submitBody(UUID.randomUUID(), "Friend fainted");
    var first = post(path(), JWKS.valid(user), body);
    var retry = post(path(), JWKS.valid(user), body);
    assertThat(first.statusCode()).isEqualTo(201);
    assertThat(retry.statusCode()).isEqualTo(200);
    assertThat(retry.body()).isEqualTo(first.body());
    assertThat(retry.headers().firstValue("Location")).isEqualTo(first.headers().firstValue("Location"));
  }

  @Test
  @DisplayName("194 AC-2 a clientRequestId reused for another request gets 409 and keeps the first one")
  void reusedClientRequestId() throws Exception {
    member(event, user, Role.RegularUser);
    var client = UUID.randomUUID();
    var first = post(path(), JWKS.valid(user), submitBody(client, "Friend fainted"));
    var other = post(path(), JWKS.valid(user), submitBody(client, "Lost my phone"));
    assertThat(first.statusCode()).isEqualTo(201);
    assertThat(other.statusCode()).isEqualTo(409);
    assertThat(get(first.headers().firstValue("Location").orElseThrow(), JWKS.valid(user)).body())
        .contains("Friend fainted");
  }

  @Test
  @DisplayName("194 AC-4 a description of 500 emoji is accepted: the limit counts characters")
  void emojiDescription() throws Exception {
    member(event, user, Role.RegularUser);
    assertThat(post(path(), JWKS.valid(user), submitBody(UUID.randomUUID(), "🚑".repeat(500)))
        .statusCode()).isEqualTo(201);
  }

  @Test
  @DisplayName("194 AC-4 a member of another event gets 404")
  void otherEvent() throws Exception {
    member(UUID.randomUUID(), user, Role.EventAdmin);
    assertThat(post(path(), JWKS.valid(user), submitBody(UUID.randomUUID(), "help")).statusCode())
        .isEqualTo(404);
    assertThat(get(path() + "/" + UUID.randomUUID(), JWKS.valid(user)).statusCode()).isEqualTo(404);
  }

  @Test
  @DisplayName("US-14 AC-1 Reports refuses cross-event reads and writes and records the attempted access")
  void refusesAndAuditsCrossEventAccess(CapturedOutput output) throws Exception {
    var other = "outsider-" + UUID.randomUUID();
    member(UUID.randomUUID(), other, Role.EventAdmin);
    member(event, user, Role.RegularUser);
    var created = post(path(), JWKS.valid(user), submitBody(UUID.randomUUID(), "private-request"));
    assertThat(created.statusCode()).isEqualTo(201);
    var resource = created.headers().firstValue("Location").orElseThrow();
    var token = JWKS.valid(other);
    var read = get(resource, token);
    var write = post(resource + "/close", token, "");
    assertThat(read.statusCode()).isEqualTo(404);
    assertThat(write.statusCode()).isEqualTo(404);
    assertThat(read.body()).doesNotContain("private-request");
    assertThat(get(resource, JWKS.valid(user)).body()).contains("Submitted");
    assertAudit(output, other, "NO_ACTIVE_MEMBERSHIP");
  }

  @Test
  @DisplayName("US-14 AC-2 a Reports administrator in X has no triage permission as a regular member of Y")
  void adminRoleDoesNotCrossEvents(CapturedOutput output) throws Exception {
    var admin = "admin-" + UUID.randomUUID();
    member(UUID.randomUUID(), admin, Role.EventAdmin);
    member(event, admin, Role.RegularUser);
    member(event, user, Role.RegularUser);
    var created = post(path(), JWKS.valid(user), submitBody(UUID.randomUUID(), "private-request"));
    assertThat(created.statusCode()).isEqualTo(201);
    var resource = created.headers().firstValue("Location").orElseThrow();
    assertThat(post(resource + "/close", JWKS.valid(admin), "").statusCode()).isEqualTo(403);
    assertThat(get(resource, JWKS.valid(user)).body()).contains("Submitted");
    assertAudit(output, admin, "MISSING_TRIAGE_PERMISSION");
  }

  private void assertAudit(CapturedOutput output, String subject, String reason) {
    assertThat(output.getAll().lines().filter(line -> line.contains("Reports event access refused"))
        .filter(line -> line.contains(subject)).toList()).isNotEmpty().allSatisfy(line -> assertThat(line)
        .contains(event.toString(), reason, "\"at\"", "\"correlationId\"")
        .doesNotContain("private-request"));
  }

  @Test
  @DisplayName("194 AC-4 a removed member gets 404")
  void removedMember() throws Exception {
    copies.upsertIfNewer(new pt.isep.labdsof.reports.domain.Membership(event, user, Role.RegularUser,
        pt.isep.labdsof.reports.domain.Membership.Status.Removed, java.util.Set.of(), 1));
    assertThat(post(path(), JWKS.valid(user), submitBody(UUID.randomUUID(), "help")).statusCode())
        .isEqualTo(404);
  }

  @Test
  @DisplayName("194 AC-4 another member cannot read someone's request")
  void otherMember() throws Exception {
    member(event, user, Role.RegularUser);
    member(event, "neighbour", Role.RegularUser);
    var created = post(path(), JWKS.valid(user), submitBody(UUID.randomUUID(), "help"));
    String id = JsonPath.read(created.body(), "$.id");
    var read = get(path() + "/" + id, JWKS.valid("neighbour"));
    var unknown = get(path() + "/" + UUID.randomUUID(), JWKS.valid("neighbour"));
    assertThat(read.statusCode()).isEqualTo(404);
    // Same answer as for a request that does not exist: existence is not revealed
    assertThat(read.body().replace(id, "")).isEqualTo(unknown.body().replaceAll(
        "help-requests/[0-9a-f-]{36}", "help-requests/"));
  }

  @Test
  @DisplayName("194 AC-4 a request id under another event is not found")
  void requestOfAnotherEvent() throws Exception {
    member(event, user, Role.RegularUser);
    var created = post(path(), JWKS.valid(user), submitBody(UUID.randomUUID(), "help"));
    String id = JsonPath.read(created.body(), "$.id");
    var otherEvent = UUID.randomUUID();
    member(otherEvent, user, Role.EventAdmin);
    assertThat(get("/api/v1/events/" + otherEvent + "/help-requests/" + id, JWKS.valid(user))
        .statusCode()).isEqualTo(404);
  }

  @Test
  @DisplayName("194 AC-4 a triage member sees the control-room view")
  void triageView() throws Exception {
    var controlRoom = UUID.randomUUID();
    copies.upsertIfNewer(new Team(controlRoom, event, "Control room",
        java.util.List.of(new Team.TeamPermission(Permission.HELP_REQUESTS_TRIAGE, java.util.Set.of())),
        java.util.Set.of(Category.Health), false, 1));
    member(event, user, Role.RegularUser);
    member(event, "operator", Role.RegularUser, controlRoom);
    var created = post(path(), JWKS.valid(user), submitBody(UUID.randomUUID(), "help"));
    String id = JsonPath.read(created.body(), "$.id");

    var read = get(path() + "/" + id, JWKS.valid("operator"));
    assertThat(read.statusCode()).isEqualTo(200);
    assertThat(JsonPath.<String>read(read.body(), "$.eventId")).isEqualTo(event.toString());
    assertThat(JsonPath.<String>read(read.body(), "$.triage.state")).isEqualTo("Pending");
    assertThat(JsonPath.<Integer>read(read.body(), "$.location.mapVersion")).isEqualTo(3);
    assertThat(JsonPath.<Object>read(read.body(), "$.category")).isNull();
  }

  @Test
  @DisplayName("194 AC-4 a malformed id is rejected")
  void malformedId() throws Exception {
    member(event, user, Role.RegularUser);
    assertThat(get(path() + "/not-a-uuid", JWKS.valid(user)).statusCode()).isEqualTo(400);
    assertThat(post("/api/v1/events/not-a-uuid/help-requests", JWKS.valid(user),
        submitBody(UUID.randomUUID(), "help")).statusCode()).isEqualTo(400);
  }

  @ParameterizedTest
  @MethodSource("invalidSubmissions")
  @DisplayName("194 AC-4 invalid submissions are rejected and nothing is saved")
  void invalidSubmissions(String body) throws Exception {
    member(event, user, Role.RegularUser);
    assertThat(post(path(), JWKS.valid(user), body).statusCode()).isEqualTo(400);
    assertThat(jdbc.sql("SELECT count(*) FROM help_request WHERE event_id = :e").param("e", event)
        .query(Long.class).single()).isZero();
  }

  static Stream<String> invalidSubmissions() {
    var client = UUID.randomUUID();
    return Stream.of(
        "{\"clientRequestId\":\"" + client + "\",\"location\":{\"mapVersion\":3,\"x\":0.4,\"y\":0.7},\"description\":\"   \"}",
        "{\"clientRequestId\":\"" + client + "\",\"location\":{\"mapVersion\":3,\"x\":0.4,\"y\":0.7},\"description\":\"" + "x".repeat(501) + "\"}",
        "{\"clientRequestId\":\"" + client + "\",\"location\":{\"mapVersion\":3,\"x\":0.4,\"y\":0.7}}",
        "{\"clientRequestId\":\"" + client + "\",\"location\":{\"mapVersion\":0,\"x\":0.4,\"y\":0.7},\"description\":\"help\"}",
        "{\"clientRequestId\":\"" + client + "\",\"location\":{\"mapVersion\":3,\"x\":1.4,\"y\":0.7},\"description\":\"help\"}",
        "{\"clientRequestId\":\"" + client + "\",\"location\":{\"mapVersion\":3,\"x\":0.4,\"y\":-0.1},\"description\":\"help\"}",
        "{\"clientRequestId\":\"" + client + "\",\"description\":\"help\"}",
        "{\"location\":{\"mapVersion\":3,\"x\":0.4,\"y\":0.7},\"description\":\"help\"}",
        "{\"clientRequestId\":\"" + client + "\",\"location\":{\"mapVersion\":3,\"x\":0.4,\"y\":0.7},\"description\":\"help\",\"requesterId\":\"someone-else\"}",
        "{\"clientRequestId\":\"" + client + "\",\"location\":{\"mapVersion\":3,\"x\":0.4,\"y\":0.7},\"description\":\"help\",\"status\":\"Closed\"}",
        "{\"clientRequestId\":\"not-a-uuid\",\"location\":{\"mapVersion\":3,\"x\":0.4,\"y\":0.7},\"description\":\"help\"}",
        "{\"clientRequestId\":\"" + client + "\",\"location\":{\"mapVersion\":3,\"x\":0.4,\"y\":0.7},\"description\":\"\\u2003\"}",
        "{\"clientRequestId\":\"" + client + "\",\"location\":{\"mapVersion\":3,\"x\":0.4,\"y\":0.7},\"description\":\"help\\u0000me\"}",
        "not json");
  }

  @Test
  @DisplayName("194 AC-4 injection text is stored and returned as plain text")
  void injectionText() throws Exception {
    member(event, user, Role.RegularUser);
    var text = "'); DROP TABLE help_request; -- <script>alert(1)</script>";
    var created = post(path(), JWKS.valid(user), submitBody(UUID.randomUUID(), text));
    assertThat(created.statusCode()).isEqualTo(201);
    String id = JsonPath.read(created.body(), "$.id");
    assertThat(JsonPath.<String>read(get(path() + "/" + id, JWKS.valid(user)).body(), "$.description"))
        .isEqualTo(text);
  }

  @Test
  @DisplayName("194 AC-4 the status endpoint stays public for the health check")
  void statusIsPublic() throws Exception {
    assertThat(get("/api/v1/status", null).statusCode()).isEqualTo(200);
  }

  @Test
  @DisplayName("148 with the actuator on the API port (no separate management port) it needs a token")
  void actuatorOnApiPortNeedsAToken() throws Exception {
    assertThat(get("/actuator/health", null).statusCode()).isEqualTo(401);
  }
}
