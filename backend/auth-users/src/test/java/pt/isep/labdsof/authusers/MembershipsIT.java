package pt.isep.labdsof.authusers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class MembershipsIT extends IntegrationTest {

  private static final String PATH = "/api/v1/me/memberships";

  @Test
  void listsOnlyTheCallersMembershipsWithLatestEventDetailsAndAssignedTeamIds() {
    var api = new ApiClient(port);
    var user = UUID.randomUUID();
    var other = UUID.randomUUID();
    var event = UUID.randomUUID();
    var otherEvent = UUID.randomUUID();
    var team = UUID.randomUUID();
    var token = JWKS.valid(user.toString());
    var otherToken = JWKS.valid(other.toString());
    assertThat(api.get(PATH, null).statusCode()).isEqualTo(401);
    assertThat(api.get(PATH, token).body()).isEqualTo("[]");
    assertThat(api.get("/api/v1/me", token).statusCode()).isEqualTo(200);
    assertThat(api.get("/api/v1/me", otherToken).statusCode()).isEqualTo(200);
    assertThat(api.get(PATH, token).body()).isEqualTo("[]");

    membership(user, event, "RegularUser");
    membership(other, otherEvent, "EventAdmin");
    jdbc.sql("""
        INSERT INTO team_assignment (user_id, event_id, team_id, assigned_at)
        VALUES (:user, :event, :team, now())
        """).param("user", user).param("event", event).param("team", team).update();

    var pending = getEntries(api, token);
    assertThat(pending.size()).isEqualTo(1);
    assertThat(pending.get(0).path("eventId").asString()).isEqualTo(event.toString());
    assertThat(pending.get(0).has("eventName")).isFalse();
    assertThat(pending.get(0).has("eventStatus")).isFalse();

    var broker = Infrastructure.adminTemplate();
    try {
      publish(broker, "event.changed", "EventChanged", event,
          "{\"name\":\"Queima do Porto\",\"status\":\"Published\",\"version\":2}");
      publish(broker, "event.changed", "EventChanged", event,
          "{\"name\":\"Obsolete\",\"status\":\"Draft\",\"version\":1}");
      publish(broker, "event.changed", "EventChanged", otherEvent,
          "{\"name\":\"Other event\",\"status\":\"Draft\",\"version\":1}");
      publish(broker, "team.changed", "TeamChanged", event,
          "{\"teamId\":\"%s\",\"version\":1,\"deleted\":false}".formatted(team));
      await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
        assertThat(jdbc.sql("SELECT name FROM event_copy WHERE event_id = :id")
            .param("id", event).query(String.class).optional()).contains("Queima do Porto");
        assertThat(jdbc.sql("SELECT name FROM event_copy WHERE event_id = :id")
            .param("id", otherEvent).query(String.class).optional()).contains("Other event");
      });
      await().atMost(Duration.ofSeconds(10)).until(() -> jdbc.sql(
          "SELECT count(*) FROM event_team_copy WHERE team_id = :id").param("id", team).query(Integer.class).single() == 1);
    } finally {
      ((CachingConnectionFactory) broker.getConnectionFactory()).destroy();
    }

    var response = api.get(PATH, token);
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).doesNotContain(otherEvent.toString(), "Obsolete");
    var entries = getEntries(api, token);
    assertThat(entries.size()).isEqualTo(1);
    var entry = entries.get(0);
    assertThat(entry.path("eventId").asString()).isEqualTo(event.toString());
    assertThat(entry.path("eventName").asString()).isEqualTo("Queima do Porto");
    assertThat(entry.path("eventStatus").asString()).isEqualTo("Published");
    assertThat(entry.path("role").asString()).isEqualTo("RegularUser");
    assertThat(entry.path("teamIds").size()).isEqualTo(1);
    assertThat(entry.path("teamIds").get(0).asString()).isEqualTo(team.toString());
    assertThat(api.get(PATH, otherToken).body()).contains(otherEvent.toString()).doesNotContain(event.toString());
  }

  @Test
  void disabledAndDeletingAccountsAreRefused() {
    var api = new ApiClient(port);
    var user = UUID.randomUUID();
    var token = JWKS.valid(user.toString());
    assertThat(api.get("/api/v1/me", token).statusCode()).isEqualTo(200);
    setStatus(user, "Disabled");
    assertThat(api.get(PATH, token).statusCode()).isEqualTo(403);
    setStatus(user, "DeletionPending");
    assertThat(api.get(PATH, token).statusCode()).isEqualTo(401);
    jdbc.sql("INSERT INTO user_tombstone(user_id, deleted_at) VALUES (:id, now())").param("id", user).update();
    assertThat(api.get(PATH, token).statusCode()).isEqualTo(401);
  }

  private void setStatus(UUID user, String status) {
    jdbc.sql("UPDATE user_account SET status = :status WHERE user_id = :user")
        .param("status", status).param("user", user).update();
  }

  private void membership(UUID user, UUID event, String role) {
    jdbc.sql("""
        INSERT INTO event_membership (user_id, event_id, role, version, created_at, updated_at)
        VALUES (:user, :event, :role, 1, now(), now())
        """).param("user", user).param("event", event).param("role", role).update();
  }

  @Test
  void deadLettersInvalidMessagesWithTheirReasonAndCorrelation() {
    var broker = Infrastructure.adminTemplate();
    try {
      var properties = new MessageProperties();
      properties.setMessageId(UUID.randomUUID().toString());
      properties.setCorrelationId("test-correlation");
      broker.send("farol.events", "event.changed", new Message("[]".getBytes(StandardCharsets.UTF_8), properties));
      var dead = broker.receive("auth-users.events-events.dlq", 10_000);
      assertThat(dead).isNotNull();
      assertThat(dead.getMessageProperties().getMessageId()).isEqualTo(properties.getMessageId());
      assertThat(dead.getMessageProperties().getCorrelationId()).isEqualTo("test-correlation");
      assertThat((String) dead.getMessageProperties().getHeader("x-farol-error")).contains("Expected Events envelope");
    } finally {
      ((CachingConnectionFactory) broker.getConnectionFactory()).destroy();
    }
  }

  private static JsonNode getEntries(ApiClient api, String token) {
    var response = api.get(PATH, token);
    assertThat(response.statusCode()).isEqualTo(200);
    return JsonMapper.builder().build().readTree(response.body());
  }

  private static void publish(RabbitTemplate broker, String key, String type, UUID event, String payload) {
    var envelope = """
        {"schemaVersion":1,"messageId":"%s","occurredAt":"2026-11-21T00:00:00Z","producer":"events",
         "actorUserId":"","type":"%s","eventId":"%s","payload":%s}
        """.formatted(UUID.randomUUID(), type, event, payload);
    broker.send("farol.events", key, new Message(envelope.getBytes(StandardCharsets.UTF_8)));
  }
}
