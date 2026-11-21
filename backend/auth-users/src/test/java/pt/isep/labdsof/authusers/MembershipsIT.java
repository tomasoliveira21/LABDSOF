package pt.isep.labdsof.authusers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;

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
      await().atMost(Duration.ofSeconds(10)).until(() -> jdbc.sql(
          "SELECT count(*) FROM event_team_copy WHERE team_id = :id").param("id", team).query(Integer.class).single() == 1);
    } finally {
      ((org.springframework.amqp.rabbit.connection.CachingConnectionFactory) broker.getConnectionFactory()).destroy();
    }

    var response = api.get(PATH, token);
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.body()).doesNotContain(otherEvent.toString(), "Obsolete");
    var entries = new tools.jackson.databind.json.JsonMapper().readValue(response.body(), List.class);
    assertThat(entries).containsExactly(Map.of("eventId", event.toString(), "eventName", "Queima do Porto",
        "eventStatus", "Published", "role", "RegularUser", "teamIds", List.of(team.toString())));
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

  private static void publish(org.springframework.amqp.rabbit.core.RabbitTemplate broker,
      String key, String type, UUID event, String payload) {
    var envelope = "{\"schemaVersion\":1,\"type\":\"%s\",\"eventId\":\"%s\",\"payload\":%s}"
        .formatted(type, event, payload);
    broker.send("farol.events", key, new Message(envelope.getBytes(StandardCharsets.UTF_8)));
  }
}
