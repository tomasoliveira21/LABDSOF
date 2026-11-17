package pt.isep.labdsof.events;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import pt.isep.labdsof.events.domain.Membership.Role;
import pt.isep.labdsof.events.domain.Membership.Status;

/** GET /api/v1/events/{eventId} over HTTP with signed tokens (spec AC-5). */
@ExtendWith(OutputCaptureExtension.class)
class EventsIT extends IntegrationTest {

  private static String code() {
    return "it-" + UUID.randomUUID().toString().substring(0, 8);
  }

  private HttpResponse<String> get(UUID eventId, UUID user) {
    return new ApiClient(port).get("/api/v1/events/" + eventId, JWKS.valid(user.toString()));
  }

  private static String pathOf(Object eventId) {
    return "/api/v1/events/" + eventId;
  }

  /** Same status, title, detail and content type; headers other than the usual ones do not differ. */
  private static Map<String, Object> comparable(HttpResponse<String> response) {
    var map = new LinkedHashMap<String, Object>(ApiClient.json(response));
    map.remove("instance");
    map.put("contentType", response.headers().firstValue("Content-Type").orElse(""));
    return map;
  }

  private static void assertNoExtraHeaders(HttpResponse<String> response) {
    assertThat(response.headers().map()).doesNotContainKeys("etag", "last-modified", "location");
  }

  @Test
  @DisplayName("192 AC-5 a non-member gets the same 404 Problem Details as for an unknown event")
  void nonMemberGetsTheSameAsUnknown() {
    var event = insertEvent("Published", null, code());
    var user = UUID.randomUUID();

    var nonMember = get(event, user);
    var unknown = get(UUID.randomUUID(), user);

    ApiClient.assertProblem(nonMember, 404);
    ApiClient.assertProblem(unknown, 404);
    assertThat(comparable(nonMember)).isEqualTo(comparable(unknown));
    assertNoExtraHeaders(nonMember);
    assertNoExtraHeaders(unknown);
    assertThat(nonMember.headers().map().keySet()).isEqualTo(unknown.headers().map().keySet());
  }

  @Test
  @DisplayName("192 AC-5 an unknown event gets 404 Problem Details")
  void unknownEventIsNotFound() {
    var response = get(UUID.randomUUID(), UUID.randomUUID());

    ApiClient.assertProblem(response, 404);
    assertThat(ApiClient.json(response).get("detail")).isEqualTo("Event not found.");
  }

  @ParameterizedTest(name = "[{index}] {0}")
  @ValueSource(strings = {"not-a-uuid", "12345", "{uuid}x"})
  @DisplayName("192 AC-5 a malformed event id gets 400 Problem Details")
  void malformedEventIdIsBadRequest(String id) {
    var path = pathOf(id.replace("{uuid}", UUID.randomUUID().toString()));

    ApiClient.assertProblem(new ApiClient(port).get(path, JWKS.valid(UUID.randomUUID().toString())), 400);
  }

  @ParameterizedTest(name = "[{index}] {0}")
  @ValueSource(strings = {"EventAdmin", "RegularUser"})
  @DisplayName("192 AC-5 a member of event X asking for event Y gets 404")
  void memberOfAnotherEventIsNotFound(String role) {
    var x = insertEvent("Published", null, code());
    var y = insertEvent("Published", null, code());
    var user = UUID.randomUUID();
    member(x, user, Role.valueOf(role), Status.Active, 1);

    assertThat(get(x, user).statusCode()).isEqualTo(200);
    ApiClient.assertProblem(get(y, user), 404);
  }

  @ParameterizedTest(name = "[{index}] {0} after {1}")
  @CsvSource(value = {"Draft,", "Suspended,Draft", "Suspended,Published"})
  @DisplayName("192 AC-5 a RegularUser gets 404 for a Draft or Suspended event")
  void regularUserCannotSeeDraftOrSuspended(String status, String previous) {
    var event = insertEvent(status, previous, "Published".equals(previous) ? code() : null);
    var user = UUID.randomUUID();
    member(event, user, Role.RegularUser, Status.Active, 1);

    ApiClient.assertProblem(get(event, user), 404);
  }

  @ParameterizedTest(name = "[{index}] {0}")
  @CsvSource({"Published,true", "Closed,false"})
  @DisplayName("192 AC-5 a RegularUser reads a Published or Closed event, with the join link only while Published")
  void regularUserReadsPublishedOrClosed(String status, boolean joinLink) {
    var code = code();
    var event = insertEvent(status, null, code);
    var user = UUID.randomUUID();
    member(event, user, Role.RegularUser, Status.Active, 1);

    var response = get(event, user);

    assertThat(response.statusCode()).isEqualTo(200);
    var body = ApiClient.json(response);
    assertThat(body.get("status")).isEqualTo(status);
    if (joinLink) {
      assertThat(body).containsEntry("joinLink", "/join/" + event + "?code=" + code);
    } else {
      assertThat(body).doesNotContainKey("joinLink");
    }
  }

  @ParameterizedTest(name = "[{index}] {0} after {1}")
  @CsvSource(value = {"Draft,", "Published,", "Suspended,Published", "Suspended,Draft", "Closed,"})
  @DisplayName("192 AC-5 an EventAdmin reads the event in every status, with the join link only while Published")
  void eventAdminReadsEveryStatus(String status, String previous) {
    var code = "Draft".equals(status) || "Draft".equals(previous) ? null : code();
    var event = insertEvent(status, previous, code);
    var admin = UUID.randomUUID();
    member(event, admin, Role.EventAdmin, Status.Active, 1);

    var response = get(event, admin);

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying(
        type -> assertThat(type).startsWith("application/json"));
    var body = ApiClient.json(response);
    assertThat(body).containsEntry("eventId", event.toString()).containsEntry("name", "Queima das Fitas 2027")
        .containsEntry("organiserName", "FAP").containsEntry("description", "Semana academica")
        .containsEntry("status", status).containsEntry("dates", Map.of("start", "2027-05-02", "end", "2027-05-09"));
    if ("Published".equals(status)) {
      assertThat(body.keySet()).containsExactlyInAnyOrder("eventId", "name", "organiserName", "dates",
          "description", "status", "joinLink");
      assertThat(body).containsEntry("joinLink", "/join/" + event + "?code=" + code);
    } else {
      assertThat(body.keySet()).containsExactlyInAnyOrder("eventId", "name", "organiserName", "dates",
          "description", "status");
    }
  }

  @Test
  @DisplayName("192 AC-5 a FarolAdmin who is not a member gets 404")
  void farolAdminWhoIsNotAMemberIsNotFound(CapturedOutput output) {
    var event = insertEvent("Published", null, code());
    var user = UUID.randomUUID();
    var messageId = UUID.randomUUID();
    var payload = "{\"userId\":\"%s\",\"role\":\"FarolAdmin\",\"granted\":true,\"version\":1}".formatted(user);
    var properties = new MessageProperties();
    properties.setMessageId(messageId.toString());
    properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
    Infrastructure.adminTemplate().send("farol.auth-users", "global-role.changed", new Message(
        LocalCopiesIT.envelope(messageId, "GlobalRoleChanged", null, payload).getBytes(UTF_8), properties));
    await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertThat(logLines(output)).anyMatch(line ->
        messageId.toString().equals(line.get("messageId")) && "GlobalRoleChanged".equals(line.get("type"))));
    var token = JWKS.sign(JWKS.claims(user.toString()).claim("email", "admin@farol.test")
        .claim("email_verified", true).claim("realm_access", Map.of("roles", java.util.List.of("FarolAdmin")))
        .build());

    ApiClient.assertProblem(new ApiClient(port).get(pathOf(event), token), 404);
  }

  @Test
  @DisplayName("192 AC-5 a removed member gets 404")
  void removedMemberIsNotFound() {
    var event = insertEvent("Published", null, code());
    var user = UUID.randomUUID();
    member(event, user, Role.EventAdmin, Status.Active, 1);
    assertThat(get(event, user).statusCode()).isEqualTo(200);
    member(event, user, Role.EventAdmin, Status.Removed, 2);

    ApiClient.assertProblem(get(event, user), 404);
  }
}
