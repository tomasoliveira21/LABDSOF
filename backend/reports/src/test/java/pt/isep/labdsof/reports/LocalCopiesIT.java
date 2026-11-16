package pt.isep.labdsof.reports;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import pt.isep.labdsof.reports.domain.Category;
import pt.isep.labdsof.reports.domain.Membership;
import pt.isep.labdsof.reports.domain.Permission;
import pt.isep.labdsof.reports.domain.Team;

/** AC-3: MembershipChanged and TeamChanged reach the local copies through the real broker (spec D7, D8). */
class LocalCopiesIT extends IntegrationTest {

  static final Duration WAIT = Duration.ofSeconds(15);

  final RabbitTemplate admin = Infrastructure.adminTemplate();
  final UUID event = UUID.randomUUID();
  final String user = "user-" + UUID.randomUUID();

  static String envelope(UUID messageId, String type, UUID eventId, String payload) {
    return """
        {"messageId":"%s","type":"%s","schemaVersion":1,"occurredAt":"2027-05-08T23:14:05Z",
         "producer":"test","eventId":%s,"actorUserId":"","payload":%s}
        """.formatted(messageId, type, eventId == null ? "null" : "\"" + eventId + "\"", payload);
  }

  String membership(String role, long version, UUID... teams) {
    var ids = String.join(",", java.util.Arrays.stream(teams).map(t -> "\"" + t + "\"").toList());
    return """
        {"userId":"%s","role":"%s","status":"Active","teamIds":[%s],"version":%d}
        """.formatted(user, role, ids, version);
  }

  static String team(UUID teamId, String name, long version) {
    return """
        {"teamId":"%s","name":"%s","permissions":[{"permission":"HelpRequests.Triage","reportTypes":[]},
         {"permission":"Reports.Review","reportTypes":["Litter","Crowding"]},
         {"permission":"HelpRequests.Respond"}],
         "handledCategories":["Health","LostPerson"],"deleted":false,"version":%d,"futureField":1}
        """.formatted(teamId, name, version);
  }

  void publish(String exchange, String routingKey, UUID messageId, String body) {
    var properties = new MessageProperties();
    properties.setMessageId(messageId.toString());
    properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
    admin.send(exchange, routingKey, new Message(body.getBytes(UTF_8), properties));
  }

  void membershipChanged(UUID messageId, String payload) {
    publish("farol.auth-users", "membership.changed", messageId,
        envelope(messageId, "MembershipChanged", event, payload));
  }

  Optional<Membership> copy() {
    return copies.membership(event, user);
  }

  long processed(UUID messageId) {
    return jdbc.sql("SELECT count(*) FROM processed_message WHERE message_id = :id").param("id", messageId)
        .query(Long.class).single();
  }

  @Test
  @DisplayName("194 AC-3 a MembershipChanged creates and updates the copy")
  void membershipApplied() {
    var team = UUID.randomUUID();
    membershipChanged(UUID.randomUUID(), membership("RegularUser", 1));
    await().atMost(WAIT).until(() -> copy().isPresent());
    membershipChanged(UUID.randomUUID(), membership("EventAdmin", 2, team));
    await().atMost(WAIT).until(() -> copy().orElseThrow().version() == 2);
    assertThat(copy().orElseThrow().role()).isEqualTo(Membership.Role.EventAdmin);
    assertThat(copy().orElseThrow().teamIds()).containsExactly(team);
  }

  @Test
  @DisplayName("194 AC-3 a duplicate event is applied once")
  void duplicate() {
    var messageId = UUID.randomUUID();
    membershipChanged(messageId, membership("RegularUser", 1));
    await().atMost(WAIT).until(() -> processed(messageId) == 1);
    // Redelivery of the same message (e.g. the producer crashed after the broker's confirm)
    membershipChanged(messageId, membership("EventAdmin", 5));
    var marker = UUID.randomUUID();
    membershipChanged(marker, membership("RegularUser", 1)); // stale, but proves the queue moved on
    await().atMost(WAIT).until(() -> processed(marker) == 1);
    assertThat(copy().orElseThrow().role()).isEqualTo(Membership.Role.RegularUser);
    assertThat(copy().orElseThrow().version()).isEqualTo(1);
    assertThat(processed(messageId)).isEqualTo(1);
  }

  @Test
  @DisplayName("194 AC-3 an older MembershipChanged is ignored")
  void olderMembership() {
    var newer = UUID.randomUUID();
    membershipChanged(newer, membership("EventAdmin", 2));
    await().atMost(WAIT).until(() -> processed(newer) == 1);
    var older = UUID.randomUUID();
    membershipChanged(older, membership("RegularUser", 1));
    await().atMost(WAIT).until(() -> processed(older) == 1);
    assertThat(copy().orElseThrow().role()).isEqualTo(Membership.Role.EventAdmin);
    assertThat(copy().orElseThrow().version()).isEqualTo(2);
  }

  @Test
  @DisplayName("194 AC-3 a TeamChanged creates the copy, an older TeamChanged is ignored")
  void olderTeam() {
    var teamId = UUID.randomUUID();
    var newer = UUID.randomUUID();
    publish("farol.events", "team.changed", newer, envelope(newer, "TeamChanged", event,
        team(teamId, "Control room", 3)));
    await().atMost(WAIT).until(() -> processed(newer) == 1);
    var older = UUID.randomUUID();
    publish("farol.events", "team.changed", older, envelope(older, "TeamChanged", event,
        team(teamId, "Old name", 2)));
    await().atMost(WAIT).until(() -> processed(older) == 1);

    var copy = copies.teams(event, Set.of(teamId));
    assertThat(copy).singleElement().satisfies(t -> {
      assertThat(t.name()).isEqualTo("Control room");
      assertThat(t.version()).isEqualTo(3);
      assertThat(t.handledCategories()).containsExactlyInAnyOrder(Category.Health, Category.LostPerson);
      assertThat(t.grants(Permission.HELP_REQUESTS_TRIAGE)).isTrue();
      assertThat(t.permissions()).contains(new Team.TeamPermission(Permission.REPORTS_REVIEW,
          Set.of("Litter", "Crowding")), new Team.TeamPermission(Permission.HELP_REQUESTS_RESPOND, Set.of()));
    });
  }

  @Test
  @DisplayName("194 AC-3 a team copy never moves to another event")
  void teamOfAnotherEvent() {
    var teamId = UUID.randomUUID();
    var first = UUID.randomUUID();
    publish("farol.events", "team.changed", first, envelope(first, "TeamChanged", event,
        team(teamId, "Control room", 1)));
    await().atMost(WAIT).until(() -> processed(first) == 1);
    var moved = UUID.randomUUID();
    var otherEvent = UUID.randomUUID();
    publish("farol.events", "team.changed", moved, envelope(moved, "TeamChanged", otherEvent,
        team(teamId, "Hijacked", 2)));
    await().atMost(WAIT).until(() -> processed(moved) == 1);
    assertThat(copies.teams(event, Set.of(teamId))).singleElement()
        .satisfies(t -> assertThat(t.name()).isEqualTo("Control room"));
    assertThat(copies.teams(otherEvent, Set.of(teamId))).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "not json",
      "{\"messageId\":\"%1$s\",\"eventId\":\"%2$s\",\"payload\":{}}",
      "{\"type\":\"MembershipChanged\",\"eventId\":\"%2$s\",\"payload\":{}}",
      "{\"messageId\":\"%1$s\",\"type\":\"MembershipChanged\",\"payload\":{\"userId\":\"u\",\"role\":\"RegularUser\",\"status\":\"Active\",\"teamIds\":[],\"version\":1}}",
      "{\"messageId\":\"%1$s\",\"type\":\"MembershipChanged\",\"eventId\":\"%2$s\",\"payload\":{\"userId\":\"u\",\"role\":\"Owner\",\"status\":\"Active\",\"teamIds\":[],\"version\":1}}",
      "{\"messageId\":\"%1$s\",\"type\":\"MembershipChanged\",\"eventId\":\"%2$s\",\"payload\":{\"userId\":\"u\",\"role\":\"RegularUser\",\"status\":\"Active\",\"teamIds\":[]}}",
      "{\"messageId\":\"%1$s\",\"type\":\"MembershipChanged\",\"eventId\":\"%2$s\",\"payload\":{\"userId\":\"u\",\"role\":\"RegularUser\",\"status\":\"Active\",\"version\":1}}",
      "{\"messageId\":\"%1$s\",\"type\":\"MembershipChanged\",\"eventId\":\"%2$s\"}",
      "{\"messageId\":\"%1$s\",\"type\":\"TeamChanged\",\"eventId\":\"%2$s\",\"payload\":{\"teamId\":\"%2$s\",\"name\":\"T\",\"permissions\":[{\"permission\":\"Everything\"}],\"handledCategories\":[],\"deleted\":false,\"version\":1}}",
      "{\"messageId\":\"%1$s\",\"type\":\"TeamChanged\",\"eventId\":\"%2$s\",\"payload\":{\"teamId\":\"%2$s\",\"name\":\"T\",\"permissions\":[],\"handledCategories\":[\"Theft\"],\"deleted\":false,\"version\":1}}",
      "{\"messageId\":\"%1$s\",\"type\":\"TeamChanged\",\"eventId\":\"%2$s\",\"payload\":{\"teamId\":\"%2$s\",\"name\":\"T\",\"handledCategories\":[],\"deleted\":false,\"version\":1}}",
      "{\"messageId\":\"%1$s\",\"type\":\"TeamChanged\",\"eventId\":\"%2$s\",\"payload\":{\"teamId\":\"%2$s\",\"name\":\"T\",\"permissions\":[{}],\"handledCategories\":[],\"deleted\":false,\"version\":1}}",
      "{\"messageId\":\"%1$s\",\"type\":\"TeamChanged\",\"eventId\":\"%2$s\",\"payload\":{\"teamId\":\"%2$s\",\"name\":\"T\",\"permissions\":[],\"deleted\":false,\"version\":1}}",
      "{\"messageId\":\"%1$s\",\"type\":\"TeamChanged\",\"eventId\":\"%2$s\",\"payload\":{\"teamId\":\"%2$s\",\"name\":\"T\",\"permissions\":[],\"handledCategories\":[],\"version\":1}}",
      "{\"messageId\":\"%1$s\",\"type\":\"TeamChanged\",\"eventId\":\"%2$s\",\"payload\":{\"teamId\":\"%2$s\",\"name\":\"T\",\"permissions\":[],\"handledCategories\":[],\"deleted\":false}}",
      "{\"messageId\":\"%1$s\",\"type\":\"TeamChanged\",\"eventId\":\"%2$s\",\"payload\":{\"teamId\":\"%2$s\",\"name\":\"\",\"permissions\":[],\"handledCategories\":[],\"deleted\":false,\"version\":1}}"
  })
  @DisplayName("194 AC-3 an invalid event goes to the dead-letter queue with its reason")
  void invalid(String template) {
    var messageId = UUID.randomUUID();
    var body = template.formatted(messageId, event);
    var routingKey = body.contains("TeamChanged") ? "team.changed" : "membership.changed";
    var exchange = body.contains("TeamChanged") ? "farol.events" : "farol.auth-users";
    var dlq = (body.contains("TeamChanged") ? "reports.events-events" : "reports.auth-users-events") + ".dlq";
    publish(exchange, routingKey, messageId, body);

    var deadLetter = await().atMost(WAIT).until(() -> receive(dlq, messageId), Optional::isPresent)
        .orElseThrow();
    assertThat(new String(deadLetter.getBody(), UTF_8)).isEqualTo(body);
    assertThat((String) deadLetter.getMessageProperties().getHeader("x-farol-error"))
        .isNotBlank();
    assertThat(processed(messageId)).isZero();
  }

  /** The dead letter with this messageId, skipping (and dropping) any other one left by earlier tests. */
  Optional<Message> receive(String queue, UUID messageId) {
    Message message;
    while ((message = admin.receive(queue, 200)) != null) {
      if (messageId.toString().equals(message.getMessageProperties().getMessageId())) {
        return Optional.of(message);
      }
    }
    return Optional.empty();
  }

  @Test
  @DisplayName("194 AC-3 an event type not handled yet is acknowledged and not stored")
  void unhandledType() {
    var messageId = UUID.randomUUID();
    publish("farol.auth-users", "user-profile.changed", messageId, envelope(messageId,
        "UserProfileChanged", null, "{\"userId\":\"u\",\"displayName\":\"Ana\"}"));
    var marker = UUID.randomUUID();
    membershipChanged(marker, membership("RegularUser", 1));
    await().atMost(WAIT).until(() -> processed(marker) == 1);
    assertThat(processed(messageId)).isZero();
    assertThat(receive("reports.auth-users-events.dlq", messageId)).isEmpty();
  }
}
