package pt.isep.labdsof.events;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import pt.isep.labdsof.events.domain.Membership.Role;
import pt.isep.labdsof.events.domain.Membership.Status;

/** POST /api/v1/events over HTTP with signed tokens (US-06). */
@ExtendWith(OutputCaptureExtension.class)
class CreateEventIT extends IntegrationTest {

  private static final String PATH = "/api/v1/events";
  private static final String BODY = """
      {"name":"Queima das Fitas 2027","organiserName":"FAP",
       "dates":{"start":"2027-05-02","end":"2027-05-09"},"description":"Semana academica"}""";

  private ApiClient api() {
    return new ApiClient(port);
  }

  private HttpResponse<String> create(UUID user, String body) {
    return api().send("POST", PATH, "Bearer " + JWKS.valid(user.toString()), body);
  }

  /** A FarolAdmin as the local copy of GlobalRoleChanged holds one (US-06 D1). */
  private UUID farolAdmin() {
    var user = UUID.randomUUID();
    farolAdmin(user, true, 1);
    return user;
  }

  private void farolAdmin(UUID user, boolean granted, long version) {
    jdbc.sql("""
        INSERT INTO global_role_copy (user_id, role, granted, version)
        VALUES (:user, 'FarolAdmin', :granted, :version)
        ON CONFLICT (user_id, role) DO UPDATE SET granted = excluded.granted, version = excluded.version
        """).param("user", user).param("granted", granted).param("version", version).update();
  }

  /** The schema is shared by every IT of the module, so a test compares against its own baseline. */
  private long events() {
    return jdbc.sql("SELECT count(*) FROM event").query(Long.class).single();
  }

  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> changes(String eventId) {
    return jdbc.sql("SELECT body::text FROM outbox WHERE type = 'EventChanged' AND body->>'eventId' = :event "
        + "ORDER BY id").param("event", eventId).query(String.class).list().stream()
        .map(body -> (Map<String, Object>) ApiClient.readJson(body).get("payload")).toList();
  }

  @Test
  @DisplayName("US-06 AC-1 the FarolAdmin creates a draft, gets it back with no join link, and it is recorded")
  void farolAdminCreatesADraft() {
    var admin = farolAdmin();

    var response = create(admin, BODY);

    assertThat(response.statusCode()).isEqualTo(201);
    var body = ApiClient.json(response);
    assertThat(body).containsEntry("status", "Draft").containsEntry("name", "Queima das Fitas 2027")
        .containsEntry("organiserName", "FAP").containsEntry("description", "Semana academica")
        .containsEntry("dates", Map.of("start", "2027-05-02", "end", "2027-05-09"));
    // A draft has no join code, so no join link reaches the body (US-11 D1)
    assertThat(body.keySet()).containsExactlyInAnyOrder("eventId", "name", "organiserName", "dates",
        "description", "status");
    var eventId = (String) body.get("eventId");
    assertThat(jdbc.sql("SELECT version FROM event WHERE event_id = CAST(:id AS uuid)").param("id", eventId)
        .query(Long.class).single()).isEqualTo(1);
    assertThat(jdbc.sql("SELECT created_by FROM event WHERE event_id = CAST(:id AS uuid)").param("id", eventId)
        .query(UUID.class).single()).isEqualTo(admin);
    assertThat(jdbc.sql("SELECT join_code FROM event WHERE event_id = CAST(:id AS uuid)").param("id", eventId)
        .query(String.class).optional()).isEmpty();
    // The first EventChanged any event ever gets (D5), without the description or a join code
    assertThat(changes(eventId)).singleElement().satisfies(payload -> {
      assertThat(payload).containsEntry("status", "Draft").containsEntry("version", 1)
          .containsEntry("name", "Queima das Fitas 2027").containsEntry("organiserName", "FAP");
      assertThat(payload).doesNotContainKeys("joinCode", "description");
    });
  }

  @Test
  @DisplayName("US-06 AC-1 the creator is not made a member, so they get the same 404 as a stranger on it")
  void theCreatorIsNotAMemberOfWhatTheyCreated() {
    var admin = farolAdmin();

    var eventId = (String) ApiClient.json(create(admin, BODY)).get("eventId");

    // D3: the FarolAdmin administers the platform; reading one event needs a membership (US-14, US-07 AC-2)
    assertThat(jdbc.sql("SELECT count(*) FROM membership_copy WHERE event_id = CAST(:id AS uuid)")
        .param("id", eventId).query(Long.class).single()).isZero();
    var read = api().get("/api/v1/events/" + eventId, JWKS.valid(admin.toString()));
    assertThat(read.statusCode()).isEqualTo(404);
    assertThat(ApiClient.json(read)).containsEntry("detail", "Event not found.");
  }

  @Test
  @DisplayName("US-06 AC-1 the record of a create carries the ids and the time, and never the event's name")
  void theCreateIsRecordedWithoutTheName(CapturedOutput output) {
    var admin = farolAdmin();

    var eventId = (String) ApiClient.json(create(admin, BODY)).get("eventId");

    assertThat(logLines(output)).anyMatch(line -> "Created an event as a draft (US-06)".equals(line.get("message"))
        && admin.toString().equals(line.get("userId")) && eventId.equals(line.get("eventId")));
    assertThat(logLines(output)).noneMatch(line -> "Queima das Fitas 2027".equals(line.get("name")));
  }

  @Test
  @DisplayName("US-06 AC-2 a signed-in caller who is not the FarolAdmin creates nothing and gets 403")
  void aCallerWhoIsNotTheFarolAdminGets403(CapturedOutput output) {
    var user = UUID.randomUUID();
    var before = events();

    var response = create(user, BODY);
    assertThat(events()).isEqualTo(before);

    assertThat(response.statusCode()).isEqualTo(403);
    assertThat(ApiClient.json(response)).containsEntry("detail", "Only the FarolAdmin may do this.");
    assertThat(logLines(output)).anyMatch(line -> user.toString().equals(line.get("userId"))
        && "createEvent".equals(line.get("operation")));
  }

  @Test
  @DisplayName("US-06 AC-2 an EventAdmin of an event is not the FarolAdmin, and a revoked FarolAdmin is not one")
  void anEventAdminAndARevokedAdminBothGet403() {
    var event = insertEvent("Published", null, "code-" + UUID.randomUUID());
    var eventAdmin = UUID.randomUUID();
    member(event, eventAdmin, Role.EventAdmin, Status.Active, 1);
    var revoked = UUID.randomUUID();
    farolAdmin(revoked, true, 1);
    farolAdmin(revoked, false, 2);

    var before = events();
    assertThat(create(eventAdmin, BODY).statusCode()).isEqualTo(403);
    assertThat(create(revoked, BODY).statusCode()).isEqualTo(403);
    assertThat(events()).isEqualTo(before); // the seeded event is the only one there is
  }

  @Test
  @DisplayName("US-06 AC-2 no token creates nothing and gets 401 with WWW-Authenticate: Bearer")
  void noTokenGets401() {
    var before = events();

    var response = api().send("POST", PATH, null, BODY);

    assertThat(response.statusCode()).isEqualTo(401);
    assertThat(response.headers().firstValue("www-authenticate").orElse("")).startsWith("Bearer");
    assertThat(events()).isEqualTo(before);
  }

  @ParameterizedTest(name = "{0}")
  @CsvSource(useHeadersInDisplayName = false, delimiter = ';', value = {
      "blank name;{\"name\":\"   \",\"organiserName\":\"FAP\",\"dates\":{\"start\":\"2027-05-02\",\"end\":\"2027-05-09\"}}",
      "blank organiser;{\"name\":\"Queima\",\"organiserName\":\"\",\"dates\":{\"start\":\"2027-05-02\",\"end\":\"2027-05-09\"}}",
      "end before start;{\"name\":\"Queima\",\"organiserName\":\"FAP\",\"dates\":{\"start\":\"2027-05-09\",\"end\":\"2027-05-02\"}}",
      "no dates;{\"name\":\"Queima\",\"organiserName\":\"FAP\"}",
      "no name;{\"organiserName\":\"FAP\",\"dates\":{\"start\":\"2027-05-02\",\"end\":\"2027-05-09\"}}",
      "unknown field;{\"name\":\"Queima\",\"organiserName\":\"FAP\",\"dates\":{\"start\":\"2027-05-02\","
          + "\"end\":\"2027-05-09\"},\"status\":\"Published\"}",
      "join code injected;{\"name\":\"Queima\",\"organiserName\":\"FAP\",\"dates\":{\"start\":\"2027-05-02\","
          + "\"end\":\"2027-05-09\"},\"joinCode\":\"mine\"}",
      "malformed json;{\"name\":\"Queima\"",
  })
  @DisplayName("US-06 AC-1 a body the contract refuses stores no event")
  void anInvalidBodyStoresNothing(String label, String body) {
    var admin = farolAdmin();
    var before = events();

    var response = create(admin, body);

    assertThat(response.statusCode()).as(label).isEqualTo(400);
    assertThat(events()).as(label).isEqualTo(before);
  }

  @ParameterizedTest(name = "{0} characters")
  @ValueSource(ints = {1, 100})
  @DisplayName("US-06 AC-1 a name and an organiser name at the bounds of the contract are accepted")
  void namesAtTheBoundsAreAccepted(int length) {
    var admin = farolAdmin();
    var value = "x".repeat(length);

    var response = create(admin, """
        {"name":"%s","organiserName":"%s","dates":{"start":"2027-05-02","end":"2027-05-09"}}"""
        .formatted(value, value));

    assertThat(response.statusCode()).isEqualTo(201);
    assertThat(ApiClient.json(response)).containsEntry("name", value).containsEntry("description", "");
  }

  @Test
  @DisplayName("US-06 AC-1 a name of 101 characters and a description of 2001 are both refused")
  void overLongValuesAreRefused() {
    var admin = farolAdmin();
    var before = events();

    assertThat(create(admin, """
        {"name":"%s","organiserName":"FAP","dates":{"start":"2027-05-02","end":"2027-05-09"}}"""
        .formatted("x".repeat(101))).statusCode()).isEqualTo(400);
    assertThat(create(admin, """
        {"name":"Queima","organiserName":"FAP","dates":{"start":"2027-05-02","end":"2027-05-09"},
         "description":"%s"}""".formatted("x".repeat(2001))).statusCode()).isEqualTo(400);
    assertThat(events()).isEqualTo(before);
  }

  @Test
  @DisplayName("US-06 AC-1 a single-day event and a 2000-character description are both accepted")
  void aSingleDayAndTheLongestDescriptionAreAccepted() {
    var admin = farolAdmin();

    var response = create(admin, """
        {"name":"Queima","organiserName":"FAP","dates":{"start":"2027-05-02","end":"2027-05-02"},
         "description":"%s"}""".formatted("x".repeat(2000)));

    assertThat(response.statusCode()).isEqualTo(201);
    assertThat(ApiClient.json(response))
        .containsEntry("dates", Map.of("start", "2027-05-02", "end", "2027-05-02"));
  }

  @Test
  @DisplayName("US-06 AC-1 two events of the same name are both created: a name is unique per team, not per event")
  void twoEventsMayShareAName() {
    var admin = farolAdmin();
    var before = events();

    var first = ApiClient.json(create(admin, BODY));
    var second = ApiClient.json(create(admin, BODY));

    assertThat(first.get("eventId")).isNotEqualTo(second.get("eventId"));
    assertThat(events()).isEqualTo(before + 2);
  }
}
