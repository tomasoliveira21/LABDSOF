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

/** POST /api/v1/events/{eventId}/publish over HTTP with signed tokens (US-11). */
@ExtendWith(OutputCaptureExtension.class)
class PublishEventIT extends IntegrationTest {

  private ApiClient api() {
    return new ApiClient(port);
  }

  private static String path(Object eventId) {
    return "/api/v1/events/" + eventId + "/publish";
  }

  private UUID draft() {
    return insertEvent("Draft", null, null);
  }

  private UUID admin(UUID eventId) {
    var user = UUID.randomUUID();
    member(eventId, user, Role.EventAdmin, Status.Active, 1);
    return user;
  }

  private HttpResponse<String> publish(Object eventId, UUID user) {
    return api().send("POST", path(eventId), "Bearer " + JWKS.valid(user.toString()));
  }

  private HttpResponse<String> get(UUID eventId, UUID user) {
    return api().get("/api/v1/events/" + eventId, JWKS.valid(user.toString()));
  }

  /** The EventChanged payloads the outbox holds for the event, oldest first. */
  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> changes(UUID eventId) {
    return jdbc.sql("SELECT body::text FROM outbox WHERE type = 'EventChanged' AND body->>'eventId' = :event "
        + "ORDER BY id").param("event", eventId.toString()).query(String.class).list().stream()
        .map(body -> (Map<String, Object>) ApiClient.readJson(body).get("payload")).toList();
  }

  private String storedJoinCode(UUID eventId) {
    return jdbc.sql("SELECT coalesce(join_code, '-') FROM event WHERE event_id = :id").param("id", eventId)
        .query(String.class).single();
  }

  private String storedStatus(UUID eventId) {
    return jdbc.sql("SELECT status FROM event WHERE event_id = :id").param("id", eventId)
        .query(String.class).single();
  }

  @Test
  @DisplayName("US-11 AC-1 an EventAdmin publishes a draft and gets the event with its join link")
  void adminPublishesADraft() {
    var event = draft();
    var user = admin(event);

    var response = publish(event, user);

    assertThat(response.statusCode()).isEqualTo(200);
    var body = ApiClient.json(response);
    assertThat(body).containsEntry("status", "Published").containsEntry("eventId", event.toString())
        .containsEntry("name", "Queima das Fitas 2027").containsEntry("organiserName", "FAP")
        .containsEntry("dates", Map.of("start", "2027-05-02", "end", "2027-05-09"));
    var joinCode = storedJoinCode(event);
    assertThat(body).containsEntry("joinLink", "/join/" + event + "?code=" + joinCode);
    assertThat(body.keySet()).containsExactlyInAnyOrder("eventId", "name", "organiserName", "dates",
        "description", "status", "joinLink");
    assertThat(jdbc.sql("SELECT version FROM event WHERE event_id = :id").param("id", event)
        .query(Long.class).single()).isEqualTo(2);
    assertThat(jdbc.sql("SELECT updated_by FROM event WHERE event_id = :id").param("id", event)
        .query(UUID.class).single()).isEqualTo(user);
    assertThat(changes(event)).singleElement().satisfies(payload -> {
      assertThat(payload).containsEntry("status", "Published").containsEntry("version", 2)
          .containsEntry("name", "Queima das Fitas 2027");
      assertThat(payload).doesNotContainKeys("joinCode", "description");
    });
  }

  @Test
  @DisplayName("US-11 AC-1 a participant who could not see the draft reads the event and its join link once it "
      + "is published")
  void participantFindsTheEventOnceItIsPublished() {
    var event = draft();
    var sofia = admin(event);
    var rita = UUID.randomUUID();
    member(event, rita, Role.RegularUser, Status.Active, 1);
    ApiClient.assertProblem(get(event, rita), 404);

    assertThat(publish(event, sofia).statusCode()).isEqualTo(200);

    var response = get(event, rita);
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(ApiClient.json(response)).containsEntry("status", "Published")
        .containsEntry("joinLink", "/join/" + event + "?code=" + storedJoinCode(event));
  }

  @Test
  @DisplayName("US-11 AC-1 the join code is unguessable, new for each event and never logged")
  void joinCodeIsUnguessableAndNeverLogged(CapturedOutput output) {
    var first = draft();
    var second = draft();

    assertThat(publish(first, admin(first)).statusCode()).isEqualTo(200);
    assertThat(publish(second, admin(second)).statusCode()).isEqualTo(200);

    var codes = List.of(storedJoinCode(first), storedJoinCode(second));
    assertThat(codes).doesNotHaveDuplicates().allSatisfy(code -> {
      assertThat(code).hasSize(27).matches("[A-Za-z0-9_-]+");
      assertThat(code).doesNotContain(first.toString()).doesNotContain(second.toString());
    });
    // The link the QR code carries is the code itself: Base64 URL-safe needs no percent-encoding (D1)
    assertThat(ApiClient.json(get(first, admin(first))).get("joinLink"))
        .isEqualTo("/join/" + first + "?code=" + codes.get(0));
    codes.forEach(code -> assertThat(output.getOut()).doesNotContain(code));
  }

  @Test
  @DisplayName("US-11 AC-1 publishing twice is refused and changes nothing")
  void publishingTwiceIsRefused() {
    var event = draft();
    var user = admin(event);
    assertThat(publish(event, user).statusCode()).isEqualTo(200);
    var joinCode = storedJoinCode(event);

    var again = publish(event, user);

    ApiClient.assertProblem(again, 409);
    assertThat(ApiClient.json(again).get("detail")).isEqualTo("This event is not a draft.");
    assertThat(storedJoinCode(event)).isEqualTo(joinCode);
    assertThat(jdbc.sql("SELECT version FROM event WHERE event_id = :id").param("id", event)
        .query(Long.class).single()).isEqualTo(2);
    assertThat(changes(event)).hasSize(1);
  }

  @ParameterizedTest(name = "[{index}] {0} after {1}")
  @CsvSource(value = {"Published,", "Suspended,Draft", "Suspended,Published", "Closed,"})
  @DisplayName("US-11 AC-1 only a draft is published")
  void onlyADraftIsPublished(String status, String previous) {
    var code = "Suspended".equals(status) && "Draft".equals(previous) ? null
        : "pe-" + UUID.randomUUID();
    var event = insertEvent(status, previous, code);
    var user = admin(event);

    var response = publish(event, user);

    ApiClient.assertProblem(response, 409);
    assertThat(ApiClient.json(response).get("detail")).isEqualTo("This event is not a draft.");
    assertThat(storedStatus(event)).isEqualTo(status);
    assertThat(storedJoinCode(event)).isEqualTo(code == null ? "-" : code);
    assertThat(changes(event)).isEmpty();
  }

  @Test
  @DisplayName("US-11 AC-1 a RegularUser cannot see a draft, so publishing it is not found")
  void regularUserCannotSeeADraft() {
    var event = draft();
    var rita = UUID.randomUUID();
    member(event, rita, Role.RegularUser, Status.Active, 1);

    var response = publish(event, rita);

    ApiClient.assertProblem(response, 404);
    assertThat(ApiClient.json(response).get("detail")).isEqualTo("Event not found.");
    assertThat(storedStatus(event)).isEqualTo("Draft");
  }

  @Test
  @DisplayName("US-11 AC-1 a RegularUser of a published event has no permission to publish")
  void regularUserOfAPublishedEventIsForbidden() {
    var event = insertEvent("Published", null, "pe-" + UUID.randomUUID());
    var rita = UUID.randomUUID();
    member(event, rita, Role.RegularUser, Status.Active, 1);

    var response = publish(event, rita);

    ApiClient.assertProblem(response, 403);
    assertThat(ApiClient.json(response).get("detail")).isEqualTo("No permission in this event.");
  }

  @Test
  @DisplayName("US-11 AC-1 an unknown event, a non-member, another event's admin and a removed member are all "
      + "the same 404")
  void everyRefusalIsTheSame404() {
    var event = draft();
    var other = draft();
    var nonMember = UUID.randomUUID();
    var otherAdmin = admin(other);
    var removed = UUID.randomUUID();
    member(event, removed, Role.EventAdmin, Status.Active, 1);
    member(event, removed, Role.EventAdmin, Status.Removed, 2);

    var unknown = publish(UUID.randomUUID(), nonMember);
    for (var response : List.of(unknown, publish(event, nonMember), publish(event, otherAdmin),
        publish(event, removed))) {
      ApiClient.assertProblem(response, 404);
      assertThat(ApiClient.json(response).get("detail")).isEqualTo("Event not found.");
    }
    assertThat(storedStatus(event)).isEqualTo("Draft");
    assertThat(storedStatus(other)).isEqualTo("Draft");
  }

  @ParameterizedTest(name = "[{index}] {0}")
  @ValueSource(strings = {"not-a-uuid", "12345", "{uuid}x"})
  @DisplayName("US-11 AC-1 a malformed event id is refused with 400")
  void malformedEventIdIsBadRequest(String id) {
    var response = publish(id.replace("{uuid}", UUID.randomUUID().toString()), UUID.randomUUID());

    ApiClient.assertProblem(response, 400);
  }

  @Test
  @DisplayName("US-11 AC-1 a body sent with the publish changes nothing")
  void bodySentWithThePublishChangesNothing() {
    var event = draft();
    var user = admin(event);

    var response = api().send("POST", path(event), "Bearer " + JWKS.valid(user.toString()),
        "{\"status\":\"Closed\",\"joinCode\":\"chosen-by-the-caller\",\"version\":99}",
        "Content-Type", "application/json");

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(storedStatus(event)).isEqualTo("Published");
    assertThat(storedJoinCode(event)).isNotEqualTo("chosen-by-the-caller").hasSize(27);
    assertThat(jdbc.sql("SELECT version FROM event WHERE event_id = :id").param("id", event)
        .query(Long.class).single()).isEqualTo(2);
  }

  @Test
  @DisplayName("US-11 AC-1 the publish is recorded with who made it and when, and never with a name or the "
      + "join code")
  void publishIsRecordedWithWhoAndWhen(CapturedOutput output) {
    var event = draft();
    var user = admin(event);
    var correlation = "publish-" + UUID.randomUUID();

    var response = api().send("POST", path(event), "Bearer " + JWKS.valid(user.toString()), null,
        "X-Correlation-Id", correlation);

    assertThat(response.statusCode()).isEqualTo(200);
    var row = jdbc.sql("SELECT updated_by, updated_at FROM event WHERE event_id = :id").param("id", event)
        .query().singleRow();
    assertThat(row.get("updated_by")).isEqualTo(user);
    assertThat(row.get("updated_at")).isNotNull();
    assertThat(logLines(output)).anySatisfy(line -> {
      assertThat(line).containsEntry("userId", user.toString()).containsEntry("eventId", event.toString())
          .containsEntry("status", "Published").containsEntry("correlationId", correlation);
      assertThat(line).containsKey("at").containsEntry("version", 2);
      assertThat(line).doesNotContainKey("joinCode");
    });
    assertThat(output.getOut()).doesNotContain("rita@farol.test").doesNotContain(storedJoinCode(event));
  }
}
