package pt.isep.labdsof.events;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import pt.isep.labdsof.events.application.EventNotFoundException;
import pt.isep.labdsof.events.application.EventQueryService;
import pt.isep.labdsof.events.domain.EventStatus;
import pt.isep.labdsof.events.domain.Membership.Role;
import pt.isep.labdsof.events.domain.Membership.Status;

/** GET /events/{eventId} on the real database, with its refusals read from the structured log (spec D5, D8). */
@ExtendWith(OutputCaptureExtension.class)
class EventQueryIT extends IntegrationTest {

  private static final String REFUSAL_MESSAGE = "Refused access to an event (US-14)";
  private static final String CODE_PREFIX = "it-";

  @Autowired EventQueryService service;

  private static String code() {
    return CODE_PREFIX + UUID.randomUUID().toString().substring(0, 8);
  }

  /** The one refusal line of this user, with its fields checked. */
  private static Map<String, Object> refusalLine(CapturedOutput output, UUID user, UUID eventId, String refusal) {
    var lines = logLines(output).stream()
        .filter(line -> REFUSAL_MESSAGE.equals(line.get("message")) && user.toString().equals(line.get("userId")))
        .toList();
    assertThat(lines).singleElement().satisfies(line -> {
      assertThat(line.get("level")).isEqualTo("WARN");
      assertThat(line.get("eventId")).isEqualTo(eventId.toString());
      assertThat(line.get("refusal")).isEqualTo(refusal);
      assertThat(Instant.parse((String) line.get("at"))).isNotNull();
    });
    return lines.get(0);
  }

  @Test
  @DisplayName("192 AC-5 a non-member gets not-found and a WARN log with userId, eventId and time, without names")
  void nonMemberGetsNotFound(CapturedOutput output) {
    var event = insertEvent("Published", null, code());
    var user = UUID.randomUUID();

    assertThatThrownBy(() -> service.get(user, event)).isInstanceOf(EventNotFoundException.class);

    var line = refusalLine(output, user, event, "NOT_A_MEMBER");
    assertThat(line.values().toString()).doesNotContain("@").doesNotContain("Queima").doesNotContain("FAP");
    assertThat(output.getAll()).doesNotContain("Queima das Fitas 2027").doesNotContain("FAP");
  }

  @Test
  @DisplayName("192 AC-5 an unknown event gets not-found and a WARN log")
  void unknownEventGetsNotFound(CapturedOutput output) {
    var event = UUID.randomUUID();
    var user = UUID.randomUUID();

    assertThatThrownBy(() -> service.get(user, event)).isInstanceOf(EventNotFoundException.class);

    refusalLine(output, user, event, "NO_SUCH_EVENT");
  }

  @Test
  @DisplayName("192 AC-5 a member of event X asking for event Y gets not-found and a WARN log with user, event "
      + "and time")
  void memberOfAnotherEventGetsNotFound(CapturedOutput output) {
    var x = insertEvent("Published", null, code());
    var y = insertEvent("Published", null, code());
    var user = UUID.randomUUID();
    member(x, user, Role.EventAdmin, Status.Active, 1);

    assertThat(service.get(user, x).eventId()).isEqualTo(x);
    assertThatThrownBy(() -> service.get(user, y)).isInstanceOf(EventNotFoundException.class);

    refusalLine(output, user, y, "NOT_A_MEMBER");
  }

  @Test
  @DisplayName("192 AC-5 a removed member gets not-found")
  void removedMemberGetsNotFound(CapturedOutput output) {
    var event = insertEvent("Published", null, code());
    var user = UUID.randomUUID();
    member(event, user, Role.EventAdmin, Status.Removed, 2);

    assertThatThrownBy(() -> service.get(user, event)).isInstanceOf(EventNotFoundException.class);

    refusalLine(output, user, event, "NOT_A_MEMBER");
  }

  @ParameterizedTest(name = "{0}")
  @CsvSource(value = {"Draft,null,null", "Published,null,code", "Suspended,Published,code", "Closed,null,code"},
      nullValues = "null")
  @DisplayName("192 AC-5 an EventAdmin reads the event in every status, the join code only while Published")
  void eventAdminReadsEveryStatus(String status, String previous, String codeMarker) {
    var code = codeMarker == null ? null : code();
    var event = insertEvent(status, previous, code);
    var user = UUID.randomUUID();
    member(event, user, Role.EventAdmin, Status.Active, 1);

    var view = service.get(user, event);

    assertThat(view.status()).isEqualTo(EventStatus.valueOf(status));
    assertThat(view.joinCode()).isEqualTo("Published".equals(status) ? code : null);
    assertThat(view.name()).isEqualTo("Queima das Fitas 2027");
    assertThat(view.organiserName()).isEqualTo("FAP");
    assertThat(view.dates().start().toString()).isEqualTo("2027-05-02");
    assertThat(view.dates().end().toString()).isEqualTo("2027-05-09");
    assertThat(view.description()).isEqualTo("Semana academica");
  }

  @ParameterizedTest(name = "{0}")
  @CsvSource(value = {"Draft,null,null,false", "Published,null,code,true", "Suspended,Draft,null,false",
      "Closed,null,code,true"}, nullValues = "null")
  @DisplayName("192 AC-5 a RegularUser reads a Published or Closed event and gets not-found for Draft or "
      + "Suspended")
  void regularUserReadsOnlyPublishedAndClosed(String status, String previous, String codeMarker, boolean sees,
      CapturedOutput output) {
    var code = codeMarker == null ? null : code();
    var event = insertEvent(status, previous, code);
    var user = UUID.randomUUID();
    member(event, user, Role.RegularUser, Status.Active, 1);

    if (sees) {
      var view = service.get(user, event);
      assertThat(view.status()).isEqualTo(EventStatus.valueOf(status));
      assertThat(view.joinCode()).isEqualTo("Published".equals(status) ? code : null);
    } else {
      assertThatThrownBy(() -> service.get(user, event)).isInstanceOf(EventNotFoundException.class);
      refusalLine(output, user, event, "NOT_VISIBLE");
    }
  }

  @Test
  @DisplayName("192 AC-5 a FarolAdmin who is not a member gets not-found, even after GlobalRoleChanged")
  void farolAdminWhoIsNotAMemberGetsNotFound(CapturedOutput output) {
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

    assertThatThrownBy(() -> service.get(user, event)).isInstanceOf(EventNotFoundException.class);

    refusalLine(output, user, event, "NOT_A_MEMBER");
  }
}
