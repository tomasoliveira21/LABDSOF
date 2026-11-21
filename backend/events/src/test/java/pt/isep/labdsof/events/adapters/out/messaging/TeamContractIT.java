package pt.isep.labdsof.events.adapters.out.messaging;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import com.networknt.schema.InputFormat;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;
import pt.isep.labdsof.events.ApiClient;
import pt.isep.labdsof.events.Infrastructure;
import pt.isep.labdsof.events.IntegrationTest;
import pt.isep.labdsof.events.adapters.out.persistence.JdbcOutbox;
import pt.isep.labdsof.events.application.TeamService;
import pt.isep.labdsof.events.domain.HelpCategory;
import pt.isep.labdsof.events.domain.Membership.Role;
import pt.isep.labdsof.events.domain.Membership.Status;
import tools.jackson.databind.json.JsonMapper;

/**
 * US-16 end to end on the broker: what the use cases write to the outbox is a TeamChanged that satisfies
 * contracts/events/team.changed.schema.json and reaches every queue bound to it (API outline §7.5).
 */
class TeamContractIT extends IntegrationTest {

  private static final RabbitTemplate ADMIN = Infrastructure.adminTemplate();
  private static final List<String> QUEUES = List.of("auth-users.events-events", "reports.events-events",
      "transports.events-events");

  @Autowired TeamService service;
  @Autowired JdbcOutbox outbox;
  @Autowired RabbitTemplate rabbit;
  @Autowired TransactionTemplate tx;
  @Autowired JsonMapper json;

  @BeforeEach
  void cleanSlate() {
    jdbc.sql("UPDATE outbox SET published_at = now() WHERE published_at IS NULL").update();
    var admin = new RabbitAdmin(ADMIN.getConnectionFactory());
    QUEUES.forEach(queue -> admin.purgeQueue(queue, false));
  }

  private UUID event() {
    return insertEvent("Published", null, "tc-" + UUID.randomUUID().toString().substring(0, 8));
  }

  private UUID admin(UUID eventId) {
    var user = UUID.randomUUID();
    member(eventId, user, Role.EventAdmin, Status.Active, 1);
    return user;
  }

  private int publishDue() {
    return new OutboxPublisher(outbox, rabbit, tx, Clock.systemUTC(), 50, Duration.ofSeconds(2)).publishDue();
  }

  private static List<Message> drain(String queue) {
    var messages = new ArrayList<Message>();
    Message message;
    while ((message = ADMIN.receive(queue, 500)) != null) {
      messages.add(message);
    }
    return messages;
  }

  @Test
  @DisplayName("US-16 AC-1 a created and renamed team publishes TeamChanged that matches its JSON Schema, in "
      + "every queue bound to team.changed")
  void createAndRenamePublishValidTeamChanged() {
    var eventId = event();
    var user = admin(eventId);

    var created = service.create(user, eventId, "Nursing");
    service.update(user, eventId, created.teamId(), "Medical", List.of(HelpCategory.Health));

    assertThat(publishDue()).isEqualTo(2);

    var schema = EventContractIT.schema(EventContractIT.TEAM);
    var envelope = EventContractIT.schema(EventContractIT.ENVELOPE);
    for (var queue : QUEUES) {
      assertThat(drain(queue)).as(queue).hasSize(2).allSatisfy(message -> {
        var body = new String(message.getBody(), UTF_8);
        assertThat(message.getMessageProperties().getType()).isEqualTo("TeamChanged");
        assertThat(message.getMessageProperties().getAppId()).isEqualTo("events");
        assertThat(schema.validate(body, InputFormat.JSON)).as(body).isEmpty();
        assertThat(envelope.validate(body, InputFormat.JSON)).as(body).isEmpty();
        assertThat(ApiClient.readJson(body)).containsEntry("actorUserId", user.toString());
      });
    }
  }

  @Test
  @DisplayName("US-16 AC-3 a deleted team publishes TeamChanged with deleted true, its permissions and a higher "
      + "version")
  void deletePublishesValidTeamChanged() {
    var eventId = event();
    var user = admin(eventId);
    var team = service.create(user, eventId, "Control room");
    jdbc.sql("""
        INSERT INTO team_permission (event_id, team_id, permission, report_types)
        VALUES (:event, :team, 'Reports.Review', CAST('{Hazard,NoWater}' AS text[]))
        """).param("event", eventId).param("team", team.teamId()).update();

    service.delete(user, eventId, team.teamId());

    var body = jdbc.sql("SELECT body::text FROM outbox WHERE type = 'TeamChanged' AND body->>'eventId' = :event "
        + "ORDER BY id DESC LIMIT 1").param("event", eventId.toString()).query(String.class).single();
    assertThat(EventContractIT.schema(EventContractIT.TEAM).validate(body, InputFormat.JSON)).as(body).isEmpty();
    var payload = json.readTree(body).get("payload");
    assertThat(payload.get("deleted").asBoolean()).isTrue();
    assertThat(payload.get("version").asLong()).isEqualTo(2);
    assertThat(payload.get("permissions").get(0).get("permission").asString()).isEqualTo("Reports.Review");
    assertThat(payload.get("permissions").get(0).get("reportTypes").size()).isEqualTo(2);
  }

  @Test
  @DisplayName("US-16 AC-1 handled categories are published in a stable order, whatever order they arrive in")
  void categoriesArePublishedInAStableOrder() {
    var eventId = event();
    var user = admin(eventId);
    var team = service.create(user, eventId, "Control room");

    service.update(user, eventId, team.teamId(), "Control room",
        List.of(HelpCategory.Other, HelpCategory.Health, HelpCategory.Infrastructure));

    var body = jdbc.sql("SELECT body::text FROM outbox WHERE type = 'TeamChanged' AND body->>'eventId' = :event "
        + "ORDER BY id DESC LIMIT 1").param("event", eventId.toString()).query(String.class).single();
    assertThat(EventContractIT.schema(EventContractIT.TEAM).validate(body, InputFormat.JSON)).isEmpty();
    var categories = json.readTree(body).get("payload").get("handledCategories");
    assertThat(List.of(categories.get(0).asString(), categories.get(1).asString(), categories.get(2).asString()))
        .containsExactly("Health", "Infrastructure", "Other");
    assertThat(categories.size()).isEqualTo(3);
  }

  @Test
  @DisplayName("US-16 AC-1 the TeamChanged of a team with every permission and report type matches its schema")
  void everyPermissionAndReportTypeMatchesTheSchema() {
    var eventId = event();
    var user = admin(eventId);
    var team = service.create(user, eventId, "Everything");
    jdbc.sql("""
        INSERT INTO team_permission (event_id, team_id, permission, report_types)
        SELECT :event, :team, permission, CASE WHEN permission = 'Reports.Review'
            THEN CAST('{QueueAtEntrance,CrowdedArea,WcProblem,NoWater,Hazard,TransportProblem,WrongInformation}'
                      AS text[]) ELSE '{}' END
        FROM unnest(ARRAY['HelpRequests.Triage', 'HelpRequests.Respond', 'Reports.Review', 'Reports.Respond',
                          'Announcements.Publish', 'Map.Edit', 'Transport.Edit', 'Entrances.Update']) AS permission
        """).param("event", eventId).param("team", team.teamId()).update();

    service.update(user, eventId, team.teamId(), "Everything",
        List.of(HelpCategory.values()));

    var body = jdbc.sql("SELECT body::text FROM outbox WHERE type = 'TeamChanged' AND body->>'eventId' = :event "
        + "ORDER BY id DESC LIMIT 1").param("event", eventId.toString()).query(String.class).single();
    assertThat(EventContractIT.schema(EventContractIT.TEAM).validate(body, InputFormat.JSON)).as(body).isEmpty();
    var payload = json.readTree(body).get("payload");
    assertThat(payload.get("permissions").size()).isEqualTo(8);
    assertThat(payload.get("handledCategories").size()).isEqualTo(5);
    assertThat(payload.get("permissions").get(2).get("permission").asString()).isEqualTo("Reports.Review");
  }
}
