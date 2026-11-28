package pt.isep.labdsof.authusers;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import tools.jackson.databind.json.JsonMapper;

/**
 * POST /api/v1/events/{eventId}/members over HTTP with signed tokens (US-06): the second half of AC-1, and the
 * only place the whole criterion can be shown — the event copy that the create in Events publishes, then the
 * name call, then the membership read back through {@code GET /me/memberships}.
 */
@ExtendWith(OutputCaptureExtension.class)
class NameEventAdminIT extends IntegrationTest {

  private static final JsonMapper JSON = JsonMapper.builder().build();

  private ApiClient api() {
    return new ApiClient(port);
  }

  private static String path(Object eventId) {
    return "/api/v1/events/" + eventId + "/members";
  }

  private UUID farolAdmin() {
    var user = UUID.randomUUID();
    seedAccount(user, "Daniel Platform");
    jdbc.sql("""
        INSERT INTO global_role_grant (user_id, role, granted_at, version)
        VALUES (:u, 'FarolAdmin', now(), 1)
        """).param("u", user).update();
    return user;
  }

  /** The copy the first EventChanged of a draft leaves here (US-06 D5). */
  private UUID eventCopy(String status) {
    var eventId = UUID.randomUUID();
    jdbc.sql("""
        INSERT INTO event_copy (event_id, name, status, version)
        VALUES (:id, 'Queima das Fitas 2027', :status, 1)
        """).param("id", eventId).param("status", status).update();
    return eventId;
  }

  private HttpResponse<String> name(Object eventId, UUID actor, UUID userId) {
    return api().send("POST", path(eventId), actor == null ? null : "Bearer " + JWKS.valid(actor.toString()),
        "{\"userId\":\"" + userId + "\",\"role\":\"EventAdmin\"}");
  }

  private String roleOf(UUID eventId, UUID userId) {
    return jdbc.sql("SELECT role FROM event_membership WHERE event_id = :e AND user_id = :u")
        .param("e", eventId).param("u", userId).query(String.class).optional().orElse("-");
  }

  private long versionOf(UUID eventId, UUID userId) {
    return jdbc.sql("SELECT version FROM event_membership WHERE event_id = :e AND user_id = :u")
        .param("e", eventId).param("u", userId).query(Long.class).single();
  }

  private long published(UUID userId) {
    return jdbc.sql("SELECT count(*) FROM outbox WHERE type = 'MembershipChanged' "
        + "AND body->'payload'->>'userId' = :u").param("u", userId.toString()).query(Long.class).single();
  }

  @Test
  @DisplayName("US-06 AC-1 the FarolAdmin names the first EventAdmin, who then reads the event in their "
      + "memberships")
  void theNamedUserBecomesTheEventAdmin() {
    var admin = farolAdmin();
    var event = eventCopy("Draft");
    var sofia = UUID.randomUUID();
    seedAccount(sofia, "Sofia Organiser");

    var response = name(event, admin, sofia);

    assertThat(response.statusCode()).isEqualTo(201);
    var body = ApiClient.json(response);
    assertThat(body).containsEntry("userId", sofia.toString()).containsEntry("displayName", "Sofia Organiser")
        .containsEntry("role", "EventAdmin");
    assertThat(JSON.readTree(response.body()).get("teamIds")).isEmpty();
    assertThat(roleOf(event, sofia)).isEqualTo("EventAdmin");
    assertThat(versionOf(event, sofia)).isEqualTo(1);
    assertThat(jdbc.sql("SELECT created_by FROM event_membership WHERE event_id = :e AND user_id = :u")
        .param("e", event).param("u", sofia).query(UUID.class).single()).isEqualTo(admin);
    assertThat(published(sofia)).isEqualTo(1);

    // AC-1 end to end: the named user now sees the event as theirs, with the role they were given
    var memberships = JSON.readTree(
        api().get("/api/v1/me/memberships", JWKS.valid(sofia.toString())).body());
    assertThat(memberships).anySatisfy(membership -> {
      assertThat(membership.get("eventId").asString()).isEqualTo(event.toString());
      assertThat(membership.get("role").asString()).isEqualTo("EventAdmin");
      assertThat(membership.get("eventName").asString()).isEqualTo("Queima das Fitas 2027");
      assertThat(membership.get("eventStatus").asString()).isEqualTo("Draft");
    });
  }

  @Test
  @DisplayName("US-06 AC-1 naming the same user again is 201, changes no version and publishes nothing new")
  void namingTheSameUserAgainChangesNothing() {
    var admin = farolAdmin();
    var event = eventCopy("Draft");
    var sofia = UUID.randomUUID();
    seedAccount(sofia, "Sofia Organiser");
    assertThat(name(event, admin, sofia).statusCode()).isEqualTo(201);

    var again = name(event, admin, sofia);

    assertThat(again.statusCode()).isEqualTo(201);
    assertThat(ApiClient.json(again)).containsEntry("role", "EventAdmin");
    assertThat(versionOf(event, sofia)).isEqualTo(1);
    assertThat(published(sofia)).isEqualTo(1); // nothing new (D6)
  }

  @Test
  @DisplayName("US-06 AC-1 a RegularUser of the event is promoted, one version later, keeping their teams")
  void aRegularUserIsPromoted() {
    var admin = farolAdmin();
    var event = eventCopy("Published");
    var rita = UUID.randomUUID();
    seedAccount(rita, "Rita Participante");
    seedMembership(event, rita, "RegularUser");
    var nursing = UUID.randomUUID();
    seedTeam(event, nursing, false);
    jdbc.sql("""
        INSERT INTO team_assignment (event_id, user_id, team_id, assigned_at, assigned_by)
        VALUES (:e, :u, :t, now(), :a)
        """).param("e", event).param("u", rita).param("t", nursing).param("a", admin).update();

    var response = name(event, admin, rita);

    assertThat(response.statusCode()).isEqualTo(201);
    assertThat(ApiClient.json(response)).containsEntry("role", "EventAdmin");
    var teamIds = JSON.readTree(response.body()).get("teamIds");
    assertThat(teamIds).hasSize(1);
    assertThat(teamIds.get(0).asString()).isEqualTo(nursing.toString());
    assertThat(versionOf(event, rita)).isEqualTo(2);
    assertThat(published(rita)).isEqualTo(1);
  }

  @Test
  @DisplayName("US-06 AC-1 an event whose copy has not arrived is the same 404 as an unknown one, nothing written")
  void anEventWithNoCopyIs404() {
    var admin = farolAdmin();
    var sofia = UUID.randomUUID();
    seedAccount(sofia, "Sofia Organiser");
    var unknown = UUID.randomUUID();

    var response = name(unknown, admin, sofia);

    // The API outline's answer: 404 until the EventChanged arrives, and the back office retries
    assertThat(response.statusCode()).isEqualTo(404);
    assertThat(ApiClient.json(response)).containsEntry("detail", "Event not found.");
    assertThat(roleOf(unknown, sofia)).isEqualTo("-");
    assertThat(published(sofia)).isZero();
  }

  @Test
  @DisplayName("US-06 AC-2 a caller who is not the FarolAdmin gets 403, and no token gets 401")
  void onlyTheFarolAdminNames() {
    var event = eventCopy("Draft");
    var sofia = UUID.randomUUID();
    seedAccount(sofia, "Sofia Organiser");
    var user = UUID.randomUUID();
    seedAccount(user, "Rita Participante");

    var refused = name(event, user, sofia);
    assertThat(refused.statusCode()).isEqualTo(403);
    assertThat(ApiClient.json(refused)).containsEntry("detail", "Only the FarolAdmin may do this.");

    var anonymous = name(event, null, sofia);
    assertThat(anonymous.statusCode()).isEqualTo(401);
    assertThat(anonymous.headers().firstValue("www-authenticate").orElse("")).startsWith("Bearer");
    assertThat(roleOf(event, sofia)).isEqualTo("-");
  }

  @Test
  @DisplayName("US-06 AC-2 an EventAdmin of the event cannot name another: this is the platform's job, not theirs")
  void anEventAdminOfTheEventCannotName() {
    var event = eventCopy("Draft");
    var eventAdmin = UUID.randomUUID();
    seedAccount(eventAdmin, "Sofia Organiser");
    seedMembership(event, eventAdmin, "EventAdmin");
    var other = UUID.randomUUID();
    seedAccount(other, "Pedro Voluntario");

    assertThat(name(event, eventAdmin, other).statusCode()).isEqualTo(403);
    assertThat(roleOf(event, other)).isEqualTo("-");
  }

  @Test
  @DisplayName("US-06 AC-1 a userId with no account is the same 404, so a guessed id reveals nothing")
  void aUserWithNoAccountIs404() {
    var admin = farolAdmin();
    var event = eventCopy("Draft");
    var nobody = UUID.randomUUID();

    var response = name(event, admin, nobody);

    assertThat(response.statusCode()).isEqualTo(404);
    assertThat(roleOf(event, nobody)).isEqualTo("-");
  }

  @ParameterizedTest(name = "{0}")
  @ValueSource(strings = {
      "{\"userId\":\"not-a-uuid\",\"role\":\"EventAdmin\"}",
      "{\"userId\":\"11111111-1111-1111-1111-111111111111\",\"role\":\"RegularUser\"}",
      "{\"userId\":\"11111111-1111-1111-1111-111111111111\"}",
      "{\"role\":\"EventAdmin\"}",
      "{\"userId\":\"11111111-1111-1111-1111-111111111111\",\"role\":\"EventAdmin\",\"version\":99}",
      "{\"userId\":",
  })
  @DisplayName("US-06 AC-1 a body the contract refuses writes no membership")
  void anInvalidBodyWritesNothing(String body) {
    var admin = farolAdmin();
    var event = eventCopy("Draft");

    var response = api().send("POST", path(event), "Bearer " + JWKS.valid(admin.toString()), body);

    assertThat(response.statusCode()).isEqualTo(400);
    assertThat(jdbc.sql("SELECT count(*) FROM event_membership WHERE event_id = :e").param("e", event)
        .query(Long.class).single()).isZero();
  }

  @Test
  @DisplayName("US-06 AC-1 a malformed event id is 400, not 404")
  void aMalformedEventIdIs400() {
    var admin = farolAdmin();
    var sofia = UUID.randomUUID();
    seedAccount(sofia, "Sofia Organiser");

    assertThat(name("not-a-uuid", admin, sofia).statusCode()).isEqualTo(400);
  }

  @Test
  @DisplayName("US-06 AC-1 the naming is recorded with the ids and the time, and never with a name")
  void theNamingIsRecordedWithoutAnyName(CapturedOutput output) {
    var admin = farolAdmin();
    var event = eventCopy("Draft");
    var sofia = UUID.randomUUID();
    seedAccount(sofia, "Sofia Unique Organiser");

    name(event, admin, sofia);

    assertThat(logLines(output)).anyMatch(line ->
        "Named an EventAdmin of an event (US-06)".equals(line.get("message"))
            && admin.toString().equals(line.get("actorUserId"))
            && event.toString().equals(line.get("eventId"))
            && sofia.toString().equals(line.get("userId")));
    assertThat(output.getAll()).doesNotContain("Sofia Unique Organiser");
  }

  @Test
  @DisplayName("US-06 AC-1 two name calls for one user at once leave one version and one MembershipChanged")
  void twoNameCallsAtOnceLeaveOneVersion() throws Exception {
    var admin = farolAdmin();
    var event = eventCopy("Draft");
    var sofia = UUID.randomUUID();
    seedAccount(sofia, "Sofia Organiser");

    try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
      var call = (java.util.concurrent.Callable<Integer>) () -> name(event, admin, sofia).statusCode();
      for (var result : executor.invokeAll(List.of(call, call))) {
        assertThat(result.get(30, java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(201);
      }
    }

    // The lock of US-18 D7 serialises them, and the second finds an EventAdmin already there (D6)
    assertThat(versionOf(event, sofia)).isEqualTo(1);
    assertThat(published(sofia)).isEqualTo(1);
  }

  @Test
  @DisplayName("US-06 AC-1 the MembershipChanged of a naming carries the role, the teams and the actor")
  void theMembershipChangedCarriesTheNaming() {
    var admin = farolAdmin();
    var event = eventCopy("Draft");
    var sofia = UUID.randomUUID();
    seedAccount(sofia, "Sofia Organiser");

    name(event, admin, sofia);

    var stored = jdbc.sql("SELECT body::text FROM outbox WHERE type = 'MembershipChanged' "
        + "AND body->'payload'->>'userId' = :u").param("u", sofia.toString()).query(String.class).single();
    var envelope = JSON.readTree(stored);
    assertThat(envelope.get("type").asString()).isEqualTo("MembershipChanged");
    assertThat(envelope.get("eventId").asString()).isEqualTo(event.toString());
    assertThat(envelope.get("actorUserId").asString()).isEqualTo(admin.toString());
    var payload = envelope.get("payload");
    assertThat(payload.get("role").asString()).isEqualTo("EventAdmin");
    assertThat(payload.get("status").asString()).isEqualTo("Active");
    assertThat(payload.get("version").asInt()).isEqualTo(1);
    assertThat(payload.get("teamIds")).isEmpty();
  }
}
