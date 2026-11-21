package pt.isep.labdsof.events.adapters.out.messaging;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.networknt.schema.InputFormat;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;
import pt.isep.labdsof.events.Infrastructure;
import pt.isep.labdsof.events.IntegrationTest;
import pt.isep.labdsof.events.adapters.out.persistence.JdbcOutbox;
import pt.isep.labdsof.events.application.Events;
import pt.isep.labdsof.events.application.OutgoingEvent;
import pt.isep.labdsof.events.application.TeamEvents;
import pt.isep.labdsof.events.application.Teams;
import pt.isep.labdsof.events.domain.DateRange;
import pt.isep.labdsof.events.domain.Event;
import pt.isep.labdsof.events.domain.EventStatus;
import pt.isep.labdsof.events.domain.HelpCategory;
import pt.isep.labdsof.events.domain.Membership;
import pt.isep.labdsof.events.domain.Permission;
import pt.isep.labdsof.events.domain.ReportType;
import pt.isep.labdsof.events.domain.Team;
import pt.isep.labdsof.events.domain.TeamPermission;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Spec D7 (SDD rule R3): what the outbox stores and sends, and the MembershipChanged that Events consumes, match
 * contracts/events.
 */
class EventContractIT extends IntegrationTest {

  static final Path EVENTS = Path.of("contracts", "events");
  static final String ENVELOPE = "envelope.schema.json";
  static final String EVENT = "event.changed.schema.json";
  static final String TEAM = "team.changed.schema.json";
  static final String MEMBERSHIP = "consumed/membership.changed.schema.json";
  static final List<String> QUEUES = List.of("auth-users.events-events", "reports.events-events",
      "transports.events-events", "badges.events-events");
  static final RabbitTemplate ADMIN = Infrastructure.adminTemplate();
  static final Duration WAIT = Duration.ofSeconds(15);

  @Autowired JdbcOutbox outbox;
  @Autowired RabbitTemplate rabbit;
  @Autowired TransactionTemplate tx;
  @Autowired JsonMapper json;
  @Autowired Events events;
  @Autowired Teams teamsPort;

  @BeforeEach
  void cleanSlate() {
    // Rows left by other test classes are not this test's concern; the broker queues start empty too.
    jdbc.sql("UPDATE outbox SET published_at = now() WHERE published_at IS NULL").update();
    var admin = new RabbitAdmin(ADMIN.getConnectionFactory());
    QUEUES.forEach(queue -> admin.purgeQueue(queue, false));
  }

  static JsonSchema schema(String file) {
    var config = SchemaValidatorsConfig.builder().formatAssertionsEnabled(true).build();
    return JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012)
        .getSchema(SchemaLocation.of(EVENTS.resolve(file).toAbsolutePath().toUri().toString()), config);
  }

  static String schemaOf(String type) {
    return "EventChanged".equals(type) ? EVENT : TEAM;
  }

  /**
   * Until US-06/US-16 add {@code OutgoingEvent} factories, this is the payload an Events row produces; the
   * factories must keep this test green.
   */
  static Map<String, Object> eventChanged(Event e) {
    return Map.of("name", e.name(), "organiserName", e.organiserName(),
        "dates", Map.of("start", e.dates().start().toString(), "end", e.dates().end().toString()),
        "status", e.status().name(), "version", e.version());
  }

  /**
   * The payload of a team row, now from the service's own factory (US-16 D6), which is what #192 asked of
   * US-16: {@code TeamEvents.changed} must keep this test green. {@code deleted} asks for the deleted version of
   * the team, as DELETE .../teams/{teamId} publishes it.
   */
  Map<String, Object> teamChanged(UUID eventId, UUID teamId, boolean deleted) {
    var team = teamsPort.liveOfEvent(eventId, teamId).orElseThrow();
    return TeamEvents.changed(deleted ? team.asDeleted() : team, UUID.randomUUID(), Instant.now()).payload();
  }

  UUID insertTeam(UUID eventId, String name, String categories) {
    var id = UUID.randomUUID();
    jdbc.sql("""
        INSERT INTO team (team_id, event_id, name, handled_categories, deleted_at, version, created_at, created_by,
                          updated_at, updated_by)
        VALUES (:id, :event, :name, CAST(:categories AS text[]), NULL, 1, now(), :by, now(), :by)
        """).param("id", id).param("event", eventId).param("name", name).param("categories", categories)
        .param("by", UUID.randomUUID()).update();
    return id;
  }

  void insertPermission(UUID eventId, UUID teamId, String permission, String reportTypes) {
    jdbc.sql("""
        INSERT INTO team_permission (event_id, team_id, permission, report_types)
        VALUES (:event, :team, :permission, CAST(:types AS text[]))
        """).param("event", eventId).param("team", teamId).param("permission", permission)
        .param("types", reportTypes).update();
  }

  /** The team A of the spec: live, two categories, two permissions. */
  UUID liveTeam(UUID eventId) {
    var team = insertTeam(eventId, "Equipa de Saude", "{Health,Safety}");
    insertPermission(eventId, team, "Reports.Review", "{QueueAtEntrance,Hazard}");
    insertPermission(eventId, team, "HelpRequests.Triage", "{}");
    return team;
  }

  UUID updatedBy(String table, String key, UUID id) {
    return jdbc.sql("SELECT updated_by FROM " + table + " WHERE " + key + " = :id").param("id", id)
        .query(UUID.class).single();
  }

  UUID add(String routingKey, String type, UUID eventId, UUID actor, Map<String, Object> payload) {
    var messageId = UUID.randomUUID();
    var event = new OutgoingEvent(messageId, OutgoingEvent.EXCHANGE, routingKey, type, 1, Instant.now(), eventId,
        actor.toString(), payload);
    tx.executeWithoutResult(status -> outbox.add(event));
    return messageId;
  }

  String storedBody(UUID messageId) {
    return jdbc.sql("SELECT body::text FROM outbox WHERE message_id = :id").param("id", messageId)
        .query(String.class).single();
  }

  List<Message> drain(String queue) {
    var messages = new ArrayList<Message>();
    Message message;
    while ((message = ADMIN.receive(queue, 500)) != null) {
      messages.add(message);
    }
    return messages;
  }

  static String text(Message message) {
    return new String(message.getBody(), UTF_8);
  }

  /** The consumed format of LocalCopiesIT.envelope, repeated here because that one is package-private. */
  static String membershipChanged(UUID messageId, UUID eventId, String payload) {
    return """
        {"messageId":"%s","type":"MembershipChanged","schemaVersion":1,"occurredAt":"2027-05-08T23:14:05Z",
         "producer":"auth-users","eventId":"%s","actorUserId":"","payload":%s}
        """.formatted(messageId, eventId, payload);
  }

  static String membershipPayload(UUID user, String role, String status, long version, UUID... teams) {
    var ids = String.join(",", Arrays.stream(teams).map(t -> "\"" + t + "\"").toList());
    var statusField = status == null ? "" : "\"status\":\"" + status + "\",";
    return "{\"userId\":\"%s\",\"role\":\"%s\",%s\"teamIds\":[%s],\"version\":%d}"
        .formatted(user, role, statusField, ids, version);
  }

  static Stream<Arguments> eventRows() {
    return Stream.of(
        Arguments.of("Draft", null, false),
        Arguments.of("Published", null, true),
        Arguments.of("Suspended", "Published", true),
        Arguments.of("Closed", null, true));
  }

  @ParameterizedTest(name = "[{index}] {0}")
  @MethodSource("eventRows")
  @DisplayName("192 D7 the EventChanged the outbox stores for an event row matches its JSON Schema, in each status")
  void storedEventChangedMatchesTheSchema(String status, String previousStatus, boolean withJoinCode) {
    var id = insertEvent(status, previousStatus, withJoinCode ? "contract-" + UUID.randomUUID() : null);
    var event = events.findById(id).orElseThrow();
    var body = storedBody(add("event.changed", "EventChanged", id, updatedBy("event", "event_id", id),
        eventChanged(event)));

    assertThat(schema(EVENT).validate(body, InputFormat.JSON)).isEmpty();
    assertThat(schema(ENVELOPE).validate(body, InputFormat.JSON)).as("envelope").isEmpty();
    assertThat(json.readTree(body).get("payload").has("joinCode")).isFalse();
  }

  @Test
  @DisplayName("192 D7 the TeamChanged the outbox stores for a live and a deleted team row matches its JSON Schema")
  void storedTeamChangedMatchesTheSchema() {
    var eventId = insertEvent("Published", null, "contract-" + UUID.randomUUID());
    var teamA = liveTeam(eventId);
    var teamB = insertTeam(eventId, "Equipa antiga", "{}");

    for (var team : List.of(teamA, teamB)) {
      var body = storedBody(add("team.changed", "TeamChanged", eventId, updatedBy("team", "team_id", team),
          teamChanged(eventId, team, team.equals(teamB))));
      assertThat(schema(TEAM).validate(body, InputFormat.JSON)).isEmpty();
      assertThat(schema(ENVELOPE).validate(body, InputFormat.JSON)).as("envelope").isEmpty();
      if (team.equals(teamB)) {
        var payload = json.readTree(body).get("payload");
        assertThat(payload.get("deleted").asBoolean()).isTrue();
        assertThat(payload.get("permissions")).isEmpty();
      }
    }
  }

  @Test
  @DisplayName("192 D7 the EventChanged and TeamChanged the outbox sends to RabbitMQ match their JSON Schemas")
  void sentMessagesMatchTheSchemas() {
    var eventId = insertEvent("Published", null, "contract-" + UUID.randomUUID());
    var team = liveTeam(eventId);
    var actor = updatedBy("event", "event_id", eventId);
    add("event.changed", "EventChanged", eventId, actor, eventChanged(events.findById(eventId).orElseThrow()));
    add("team.changed", "TeamChanged", eventId, actor, teamChanged(eventId, team, false));

    var published = new OutboxPublisher(outbox, rabbit, tx, Clock.systemUTC(), 50, Duration.ofSeconds(2))
        .publishDue();
    assertThat(published).isEqualTo(2);

    for (var queue : List.of("reports.events-events", "auth-users.events-events")) {
      var messages = drain(queue);
      assertThat(messages).as(queue).extracting(m -> m.getMessageProperties().getType())
          .containsExactly("EventChanged", "TeamChanged");
      for (var message : messages) {
        var type = message.getMessageProperties().getType();
        assertThat(message.getMessageProperties().getAppId()).isEqualTo("events");
        assertThat(schema(schemaOf(type)).validate(text(message), InputFormat.JSON)).as(queue + " " + type)
            .isEmpty();
      }
    }
  }

  static Stream<Arguments> consumedMemberships() {
    return Stream.of(
        Arguments.of("RegularUser, Active, no team", "RegularUser", "Active", Membership.Status.Active, 0),
        Arguments.of("EventAdmin, Removed, one team", "EventAdmin", "Removed", Membership.Status.Removed, 1),
        Arguments.of("RegularUser without status (read as Active)", "RegularUser", null,
            Membership.Status.Active, 0));
  }

  @ParameterizedTest(name = "[{index}] {0}")
  @MethodSource("consumedMemberships")
  @DisplayName("192 D7 the MembershipChanged messages Events consumes match consumed/membership.changed.schema.json "
      + "and are applied")
  void consumedMembershipsMatchTheSchemaAndAreApplied(String label, String role, String status,
      Membership.Status expected, int teams) {
    var event = UUID.randomUUID();
    var user = UUID.randomUUID();
    var messageId = UUID.randomUUID();
    var teamIds = Stream.generate(UUID::randomUUID).limit(teams).toArray(UUID[]::new);
    var body = membershipChanged(messageId, event, membershipPayload(user, role, status, 1, teamIds));
    assertThat(schema(MEMBERSHIP).validate(body, InputFormat.JSON)).isEmpty();

    var properties = new MessageProperties();
    properties.setMessageId(messageId.toString());
    properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
    ADMIN.send("farol.auth-users", "membership.changed", new Message(body.getBytes(UTF_8), properties));

    await().atMost(WAIT).until(() -> copies.membership(event, user).isPresent());
    var copy = copies.membership(event, user).orElseThrow();
    assertThat(copy.status()).isEqualTo(expected);
    assertThat(copy.role()).isEqualTo(Membership.Role.valueOf(role));
  }

  static Arguments broken(String label, String base, String schemaFile, Consumer<ObjectNode> breakIt) {
    return Arguments.of(label, base, schemaFile, breakIt);
  }

  static ObjectNode payload(ObjectNode tree) {
    return (ObjectNode) tree.get("payload");
  }

  static ObjectNode permission(ObjectNode tree, int index) {
    return (ObjectNode) ((ArrayNode) payload(tree).get("permissions")).get(index);
  }

  static Stream<Arguments> invalidMessages() {
    return Stream.of(
        broken("EventChanged without payload.organiserName", "event", EVENT,
            tree -> payload(tree).remove("organiserName")),
        broken("EventChanged without payload.dates.end", "event", EVENT,
            tree -> ((ObjectNode) payload(tree).get("dates")).remove("end")),
        broken("EventChanged with a dates.start that is not a date", "event", EVENT,
            tree -> ((ObjectNode) payload(tree).get("dates")).put("start", "02/05/2027")),
        broken("EventChanged with status Cancelled", "event", EVENT,
            tree -> payload(tree).put("status", "Cancelled")),
        broken("EventChanged with payload.version as a string", "event", EVENT,
            tree -> payload(tree).put("version", "3")),
        broken("EventChanged with the join code in the payload", "event", EVENT,
            tree -> payload(tree).put("joinCode", "ABC123")),
        broken("EventChanged with a blank name", "event", EVENT, tree -> payload(tree).put("name", "   ")),
        broken("EventChanged with a name of 101 characters", "event", EVENT,
            tree -> payload(tree).put("name", "a".repeat(101))),
        broken("EventChanged with a null eventId", "event", EVENT, tree -> tree.putNull("eventId")),
        broken("EventChanged from another producer", "event", EVENT, tree -> tree.put("producer", "auth-users")),
        broken("EventChanged without messageId (the envelope through allOf)", "event", EVENT,
            tree -> tree.remove("messageId")),
        broken("TeamChanged without payload.deleted", "team", TEAM, tree -> payload(tree).remove("deleted")),
        broken("TeamChanged with an unknown permission", "team", TEAM,
            tree -> permission(tree, 0).put("permission", "Map.Delete")),
        // The factory lists the permissions in the order of features.md §4.2: HelpRequests.Triage, then
        // Reports.Review (US-16 D6)
        broken("TeamChanged with report types on a permission other than Reports.Review", "team", TEAM,
            tree -> permission(tree, 0).putArray("reportTypes").add("Hazard")),
        broken("TeamChanged with an unknown report type", "team", TEAM,
            tree -> permission(tree, 1).putArray("reportTypes").add("Fire")),
        broken("TeamChanged without reportTypes on a permission", "team", TEAM,
            tree -> permission(tree, 1).remove("reportTypes")),
        broken("TeamChanged with an unknown handled category", "team", TEAM,
            tree -> payload(tree).putArray("handledCategories").add("Fire")),
        broken("TeamChanged with a teamId that is not a uuid", "team", TEAM,
            tree -> payload(tree).put("teamId", "team-1")),
        broken("MembershipChanged without an eventId (null)", "membership", MEMBERSHIP,
            tree -> tree.putNull("eventId")),
        broken("MembershipChanged with role FarolAdmin", "membership", MEMBERSHIP,
            tree -> payload(tree).put("role", "FarolAdmin")),
        broken("MembershipChanged with status Pending", "membership", MEMBERSHIP,
            tree -> payload(tree).put("status", "Pending")),
        broken("MembershipChanged with schemaVersion 2", "membership", MEMBERSHIP,
            tree -> tree.put("schemaVersion", 2)),
        broken("MembershipChanged without teamIds", "membership", MEMBERSHIP,
            tree -> payload(tree).remove("teamIds")),
        broken("envelope with schemaVersion 0", "event", ENVELOPE, tree -> tree.put("schemaVersion", 0)));
  }

  private String validBody(String base) {
    var eventId = UUID.randomUUID();
    var actor = UUID.randomUUID().toString();
    switch (base) {
      case "event" -> {
        var event = new Event(eventId, "Queima das Fitas 2027", "FAP",
            new DateRange(LocalDate.of(2027, 5, 2), LocalDate.of(2027, 5, 9)), "", EventStatus.Published, "code", 3);
        return json.writeValueAsString(new OutgoingEvent(UUID.randomUUID(), OutgoingEvent.EXCHANGE, "event.changed",
            "EventChanged", 1, Instant.now(), eventId, actor, eventChanged(event)).envelope());
      }
      case "team" -> {
        var team = new Team(UUID.randomUUID(), eventId, "Equipa de Saude", List.of(
            new TeamPermission(Permission.REPORTS_REVIEW, Set.of(ReportType.QueueAtEntrance)),
            TeamPermission.of(Permission.HELP_REQUESTS_TRIAGE)), Set.of(HelpCategory.Health), false, 1);
        return json.writeValueAsString(new OutgoingEvent(UUID.randomUUID(), OutgoingEvent.EXCHANGE, "team.changed",
            "TeamChanged", 1, Instant.now(), eventId, actor,
            TeamEvents.changed(team, UUID.randomUUID(), Instant.now()).payload()).envelope());
      }
      default -> {
        return membershipChanged(UUID.randomUUID(), eventId,
            membershipPayload(UUID.randomUUID(), "RegularUser", "Active", 1));
      }
    }
  }

  @ParameterizedTest(name = "[{index}] {0}")
  @MethodSource("invalidMessages")
  @DisplayName("192 D7 a message with a missing field, a wrong type or an unexpected value is refused by its schema")
  void brokenMessagesAreRefused(String label, String base, String schemaFile, Consumer<ObjectNode> breakIt) {
    var valid = validBody(base);
    assertThat(schema(schemaFile).validate(valid, InputFormat.JSON)).as("positive control").isEmpty();

    var tree = (ObjectNode) json.readTree(valid);
    breakIt.accept(tree);

    assertThat(schema(schemaFile).validate(json.writeValueAsString(tree), InputFormat.JSON)).isNotEmpty();
  }
}
