package pt.isep.labdsof.transports;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static pt.isep.labdsof.transports.Fixtures.envelope;
import static pt.isep.labdsof.transports.Fixtures.eventChanged;
import static pt.isep.labdsof.transports.Fixtures.eventPayload;
import static pt.isep.labdsof.transports.Fixtures.membershipChanged;
import static pt.isep.labdsof.transports.Fixtures.membershipPayload;
import static pt.isep.labdsof.transports.Fixtures.teamChanged;
import static pt.isep.labdsof.transports.Fixtures.teamPayload;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import pt.isep.labdsof.transports.domain.EventStatus;
import pt.isep.labdsof.transports.domain.Membership;
import pt.isep.labdsof.transports.domain.Permission;

/** The three copies are fed from Transports' two queues through the real broker as the transports user (spec D3). */
@ExtendWith(OutputCaptureExtension.class)
class LocalCopiesIT extends IntegrationTest {

  static final Duration WAIT = Duration.ofSeconds(15);
  static final String AUTH_USERS = "farol.auth-users";
  static final String EVENTS = "farol.events";
  static final String AUTH_USERS_DLQ = "transports.auth-users-events.dlq";
  static final String EVENTS_DLQ = "transports.events-events.dlq";

  final RabbitTemplate admin = Infrastructure.adminTemplate();
  final UUID event = UUID.randomUUID();
  final UUID user = UUID.randomUUID();
  final UUID team = UUID.randomUUID();

  void publish(String exchange, String routingKey, UUID messageId, String body, String correlationId) {
    var properties = new MessageProperties();
    properties.setMessageId(messageId.toString());
    properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
    properties.setCorrelationId(correlationId);
    admin.send(exchange, routingKey, new Message(body.getBytes(UTF_8), properties));
  }

  void publish(String exchange, String routingKey, UUID messageId, String body) {
    publish(exchange, routingKey, messageId, body, null);
  }

  UUID membership(String role, String status, long version, UUID... teams) {
    var messageId = UUID.randomUUID();
    publish(AUTH_USERS, "membership.changed", messageId,
        membershipChanged(messageId, event, membershipPayload(user, role, status, version, teams)));
    return messageId;
  }

  UUID eventStatus(String status, long version) {
    var messageId = UUID.randomUUID();
    publish(EVENTS, "event.changed", messageId, eventChanged(messageId, event, eventPayload(status, version)));
    return messageId;
  }

  UUID teamOf(UUID eventId, boolean deleted, long version, String... permissions) {
    var messageId = UUID.randomUUID();
    publish(EVENTS, "team.changed", messageId,
        teamChanged(messageId, eventId, teamPayload(team, deleted, version, permissions)));
    return messageId;
  }

  long processed(UUID messageId) {
    return jdbc.sql("SELECT count(*) FROM processed_message WHERE message_id = :id").param("id", messageId)
        .query(Long.class).single();
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

  /** A valid message on each queue, awaited in processed_message: everything sent before them has been handled. */
  void awaitQueuesMovedOn() {
    var onAuthUsers = UUID.randomUUID();
    publish(AUTH_USERS, "membership.changed", onAuthUsers, membershipChanged(onAuthUsers, UUID.randomUUID(),
        membershipPayload(UUID.randomUUID(), "RegularUser", "Active", 1)));
    var onEvents = UUID.randomUUID();
    publish(EVENTS, "event.changed", onEvents, eventChanged(onEvents, UUID.randomUUID(), eventPayload("Draft", 1)));
    await().atMost(WAIT).until(() -> processed(onAuthUsers) == 1 && processed(onEvents) == 1);
  }

  @Test
  @DisplayName("193 AC-5 a MembershipChanged creates the membership copy, an older one is ignored")
  void membershipApplied() {
    var teamId = UUID.randomUUID();
    var second = membership("EventAdmin", "Active", 2, teamId);
    await().atMost(WAIT).until(() -> processed(second) == 1);
    var older = membership("RegularUser", "Removed", 1);
    await().atMost(WAIT).until(() -> processed(older) == 1);

    assertThat(copies.membership(event, user)).hasValueSatisfying(m -> {
      assertThat(m.role()).isEqualTo(Membership.Role.EventAdmin);
      assertThat(m.status()).isEqualTo(Membership.Status.Active);
      assertThat(m.teamIds()).containsExactly(teamId);
      assertThat(m.version()).isEqualTo(2);
    });
  }

  @Test
  @DisplayName("193 AC-5 a MembershipChanged without status is stored as Active")
  void missingStatusIsActive() {
    var messageId = membership("RegularUser", null, 1);
    await().atMost(WAIT).until(() -> processed(messageId) == 1);
    assertThat(copies.membership(event, user)).hasValueSatisfying(m -> assertThat(m.isActive()).isTrue());
  }

  @Test
  @DisplayName("193 AC-5 an EventChanged creates the event copy, an older one is ignored")
  void eventApplied() {
    var suspended = eventStatus("Suspended", 3);
    await().atMost(WAIT).until(() -> processed(suspended) == 1);
    var older = eventStatus("Published", 2);
    await().atMost(WAIT).until(() -> processed(older) == 1);

    assertThat(copies.event(event)).hasValueSatisfying(e -> {
      assertThat(e.status()).isEqualTo(EventStatus.Suspended);
      assertThat(e.version()).isEqualTo(3);
    });
  }

  @Test
  @DisplayName("193 AC-5 a TeamChanged creates the team copy with its permissions, an older one is ignored")
  void teamApplied() {
    var second = teamOf(event, false, 2, "Transport.Edit", "Map.Edit");
    await().atMost(WAIT).until(() -> processed(second) == 1);
    var older = teamOf(event, true, 1);
    await().atMost(WAIT).until(() -> processed(older) == 1);

    assertThat(copies.team(team)).hasValueSatisfying(t -> {
      assertThat(t.eventId()).isEqualTo(event);
      assertThat(t.permissions()).containsExactlyInAnyOrder(Permission.TRANSPORT_EDIT, Permission.MAP_EDIT);
      assertThat(t.deleted()).isFalse();
      assertThat(t.version()).isEqualTo(2);
    });
  }

  @Test
  @DisplayName("193 AC-5 a duplicate event is applied once")
  void duplicate() {
    var messageId = UUID.randomUUID();
    publish(EVENTS, "event.changed", messageId, eventChanged(messageId, event, eventPayload("Draft", 1)));
    await().atMost(WAIT).until(() -> processed(messageId) == 1);
    // Redelivery of the same message (the producer crashed after the broker's confirm)
    publish(EVENTS, "event.changed", messageId, eventChanged(messageId, event, eventPayload("Published", 5)));
    awaitQueuesMovedOn();

    assertThat(copies.event(event)).hasValueSatisfying(e -> assertThat(e.version()).isEqualTo(1));
    assertThat(processed(messageId)).isEqualTo(1);
  }

  @Test
  @DisplayName("193 AC-5 a team copy never moves to another event, and the attempt is logged as a warning")
  void teamNeverMovesToAnotherEvent(CapturedOutput output) {
    var first = teamOf(event, false, 1);
    await().atMost(WAIT).until(() -> processed(first) == 1);
    var elsewhere = teamOf(UUID.randomUUID(), false, 2, "Transport.Edit");
    await().atMost(WAIT).until(() -> processed(elsewhere) == 1);

    assertThat(copies.team(team)).hasValueSatisfying(t -> {
      assertThat(t.eventId()).isEqualTo(event);
      assertThat(t.permissions()).isEmpty();
    });
    await().atMost(WAIT).untilAsserted(() -> assertThat(logLines(output)).anyMatch(line ->
        elsewhere.toString().equals(line.get("messageId")) && "WARN".equals(line.get("level"))));
  }

  private static Stream<Arguments> invalidMessages() {
    var id = "11111111-2222-3333-4444-555555555555";
    var good = membershipPayload(UUID.fromString(id), "RegularUser", "Active", 1);
    return Stream.of(
        Arguments.of("bad JSON", AUTH_USERS, "membership.changed", AUTH_USERS_DLQ, "not json"),
        Arguments.of("null", AUTH_USERS, "membership.changed", AUTH_USERS_DLQ, "null"),
        Arguments.of("no eventId", AUTH_USERS, "membership.changed", AUTH_USERS_DLQ,
            "{\"messageId\":\"%1$s\",\"type\":\"MembershipChanged\",\"schemaVersion\":1,"
                + "\"occurredAt\":\"2027-05-08T23:14:05Z\",\"producer\":\"auth-users\",\"actorUserId\":\"\","
                + "\"payload\":" + good + "}"),
        Arguments.of("schemaVersion 2", AUTH_USERS, "membership.changed", AUTH_USERS_DLQ,
            membershipTemplate(good).replace("\"schemaVersion\":1", "\"schemaVersion\":2")),
        Arguments.of("no messageId", AUTH_USERS, "membership.changed", AUTH_USERS_DLQ,
            membershipTemplate(good).replace("\"messageId\":\"%1$s\",", "")),
        Arguments.of("no type", AUTH_USERS, "membership.changed", AUTH_USERS_DLQ,
            membershipTemplate(good).replace("\"type\":\"MembershipChanged\",", "")),
        Arguments.of("no occurredAt", EVENTS, "event.changed", EVENTS_DLQ,
            eventTemplate("EventChanged", eventPayload("Draft", 1))
                .replace("\"occurredAt\":\"2027-05-08T23:14:05Z\",", "")),
        Arguments.of("no producer", EVENTS, "event.changed", EVENTS_DLQ,
            eventTemplate("EventChanged", eventPayload("Draft", 1)).replace("\"producer\":\"events\",", "")),
        Arguments.of("no actorUserId", EVENTS, "team.changed", EVENTS_DLQ,
            eventTemplate("TeamChanged", teamPayload(UUID.fromString(id), false, 1))
                .replace("\"actorUserId\":\"\",", "")),
        Arguments.of("unknown role", AUTH_USERS, "membership.changed", AUTH_USERS_DLQ,
            membershipTemplate(good.replace("RegularUser", "Owner"))),
        Arguments.of("a null team id", AUTH_USERS, "membership.changed", AUTH_USERS_DLQ,
            membershipTemplate(good.replace("\"teamIds\":[]", "\"teamIds\":[null]"))),
        Arguments.of("no membership version", AUTH_USERS, "membership.changed", AUTH_USERS_DLQ,
            membershipTemplate(good.replace(",\"version\":1", ""))),
        Arguments.of("unknown event status", EVENTS, "event.changed", EVENTS_DLQ,
            eventTemplate("EventChanged", eventPayload("Open", 1))),
        Arguments.of("no event version", EVENTS, "event.changed", EVENTS_DLQ,
            eventTemplate("EventChanged", eventPayload("Draft", 1).replace(",\"version\":1", ""))),
        Arguments.of("unknown permission", EVENTS, "team.changed", EVENTS_DLQ,
            eventTemplate("TeamChanged", teamPayload(UUID.fromString(id), false, 1, "Bus.Drive"))),
        Arguments.of("no teamId", EVENTS, "team.changed", EVENTS_DLQ,
            eventTemplate("TeamChanged", teamPayload(UUID.fromString(id), false, 1)
                .replace("\"teamId\":\"" + id + "\",", ""))),
        Arguments.of("a null permission", EVENTS, "team.changed", EVENTS_DLQ,
            eventTemplate("TeamChanged", teamPayload(UUID.fromString(id), false, 1)
                .replace("\"permissions\":[]", "\"permissions\":[null]"))),
        Arguments.of("no deleted", EVENTS, "team.changed", EVENTS_DLQ,
            eventTemplate("TeamChanged", teamPayload(UUID.fromString(id), false, 1)
                .replace("\"deleted\":false,", ""))));
  }

  /** An envelope whose messageId and eventId are filled with {@code formatted(messageId, event)}. */
  private static String membershipTemplate(String payload) {
    return template("MembershipChanged", "auth-users", payload);
  }

  private static String eventTemplate(String type, String payload) {
    return template(type, "events", payload);
  }

  private static String template(String type, String producer, String payload) {
    return """
        {"messageId":"%%1$s","type":"%s","schemaVersion":1,"occurredAt":"2027-05-08T23:14:05Z",\
        "producer":"%s","eventId":"%%2$s","actorUserId":"","payload":%s}""".formatted(type, producer, payload);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("invalidMessages")
  @DisplayName("193 AC-5 an invalid event goes to the dead-letter queue with its reason and is not retried")
  void invalid(String name, String exchange, String routingKey, String dlq, String template) {
    var messageId = UUID.randomUUID();
    var body = template.formatted(messageId, event);
    publish(exchange, routingKey, messageId, body);

    var deadLetter = await().atMost(WAIT).until(() -> receive(dlq, messageId), Optional::isPresent)
        .orElseThrow();
    assertThat(new String(deadLetter.getBody(), UTF_8)).isEqualTo(body);
    assertThat(deadLetter.getMessageProperties().<String>getHeader("x-farol-error")).matches("(?s)^[A-Za-z]+: .*");
    assertThat(deadLetter.getMessageProperties().getReceivedDeliveryMode())
        .isEqualTo(MessageDeliveryMode.PERSISTENT);
    assertThat(processed(messageId)).isZero();

    // Not retried: once the queues have moved on, no second dead letter of this message exists
    awaitQueuesMovedOn();
    assertThat(receive(dlq, messageId)).isEmpty();
  }

  @Test
  @DisplayName("193 AC-5 a database failure is retried four times, then dead-lettered")
  void databaseFailureIsRetriedThenDeadLettered() {
    var n = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    jdbc.sql("CREATE SEQUENCE test_attempts_" + n).update();
    jdbc.sql("CREATE FUNCTION test_fail_" + n + "() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN "
        + "PERFORM nextval('test_attempts_" + n + "'); RAISE EXCEPTION 'test: database unavailable'; END $$")
        .update();
    jdbc.sql("CREATE TRIGGER test_fail_" + n + " BEFORE INSERT OR UPDATE ON membership_copy FOR EACH ROW "
        + "WHEN (NEW.user_id = '" + user + "') EXECUTE FUNCTION test_fail_" + n + "()").update();
    try {
      var messageId = membership("RegularUser", "Active", 1);

      var deadLetter = await().atMost(WAIT).until(() -> receive(AUTH_USERS_DLQ, messageId), Optional::isPresent)
          .orElseThrow();
      assertThat(deadLetter.getMessageProperties().<String>getHeader("x-farol-error"))
          .contains("database unavailable");
      assertThat(jdbc.sql("SELECT last_value FROM test_attempts_" + n).query(Long.class).single()).isEqualTo(4L);
      assertThat(processed(messageId)).isZero();
      assertThat(copies.membership(event, user)).isEmpty();
    } finally {
      jdbc.sql("DROP TRIGGER IF EXISTS test_fail_" + n + " ON membership_copy").update();
      jdbc.sql("DROP FUNCTION IF EXISTS test_fail_" + n + "()").update();
      jdbc.sql("DROP SEQUENCE IF EXISTS test_attempts_" + n).update();
    }
  }

  @ParameterizedTest(name = "{0}")
  @CsvSource({"PointOfInterestChanged,farol.events,point-of-interest.changed,events",
      "UserStatusChanged,farol.auth-users,user-status.changed,auth-users",
      "UserDeleted,farol.auth-users,user.deleted,auth-users",
      "SomethingNew,farol.events,team.changed,events"})
  @DisplayName("193 AC-5 the event types Transports does not handle yet are acknowledged and not stored")
  void otherTypesAreAcknowledged(String type, String exchange, String routingKey, String producer,
      CapturedOutput output) {
    var messageId = UUID.randomUUID();
    publish(exchange, routingKey, messageId, envelope(messageId, type, producer, event,
        "{\"userId\":\"u\",\"name\":\"Ana-Test-Name\"}"));

    await().atMost(WAIT).untilAsserted(() -> assertThat(logLines(output)).anyMatch(line ->
        messageId.toString().equals(line.get("messageId")) && type.equals(line.get("type"))
            && "INFO".equals(line.get("level"))));
    awaitQueuesMovedOn();
    assertThat(processed(messageId)).isZero();
    assertThat(receive(AUTH_USERS_DLQ, messageId)).isEmpty();
    assertThat(receive(EVENTS_DLQ, messageId)).isEmpty();
    assertThat(output.getAll()).doesNotContain("Ana-Test-Name");
  }

  /** Compose starts Transports and rabbitmq-setup together, so the broker may refuse its user at first (D3). */
  @Test
  @DisplayName("193 AC-5 Transports starts before its broker user exists and consumes once it is created")
  void startsBeforeBrokerUser() {
    var late = "transports-" + UUID.randomUUID();
    try (var transports = Infrastructure.startTransports(
        Map.of("spring.rabbitmq.username", late, "spring.rabbitmq.password", "test-only-late"))) {
      assertThat(transports.isRunning()).isTrue();
      Infrastructure.rabbitmqctl("add_user", late, "test-only-late");
      Infrastructure.rabbitmqctl("set_permissions", late, "^$", "^farol\\.dead-letters$", "^transports\\..*");
      await().atMost(Duration.ofSeconds(30)).until(() ->
          Infrastructure.rabbitmqctl("list_channels", "user", "consumer_count")
              .lines().filter(line -> line.startsWith(late + "\t")).anyMatch(line -> !line.endsWith("\t0")));
    }
    // Only after the context has closed its connections: deleting a connected user makes the broker cut them
    Infrastructure.rabbitmqctl("delete_user", late);
  }

  @Test
  @DisplayName("193 AC-5 Transports starts while its queues are missing and consumes once they exist")
  void startsWhileTheQueuesAreMissing() {
    var vhost = "transports-missing-" + UUID.randomUUID();
    Infrastructure.rabbitmqctl("add_vhost", vhost);
    var connection = new CachingConnectionFactory(Infrastructure.RABBIT.getHost(),
        Infrastructure.RABBIT.getAmqpPort());
    try {
      Infrastructure.rabbitmqctl("set_permissions", "-p", vhost, "transports", "^$", "^farol\\.dead-letters$",
          "^transports\\..*");
      Infrastructure.rabbitmqctl("set_permissions", "-p", vhost, Infrastructure.RABBIT.getAdminUsername(),
          ".*", ".*", ".*");
      try (var transports = Infrastructure.startTransports(Map.of("spring.rabbitmq.virtual-host", vhost))) {
        assertThat(transports.isRunning()).isTrue();
        connection.setUsername(Infrastructure.RABBIT.getAdminUsername());
        connection.setPassword(Infrastructure.RABBIT.getAdminPassword());
        connection.setVirtualHost(vhost);
        var declare = new RabbitAdmin(connection);
        for (var queue : Set.of("transports.auth-users-events", "transports.events-events")) {
          declare.declareQueue(new Queue(queue, true));
        }
        await().atMost(Duration.ofSeconds(60)).until(() ->
            Infrastructure.rabbitmqctl("list_queues", "-p", vhost, "name", "consumers").lines()
                .filter(line -> line.startsWith("transports.")).filter(line -> !line.endsWith("\t0")).count() == 2);
      }
    } finally {
      connection.destroy();
      Infrastructure.rabbitmqctl("delete_vhost", vhost);
    }
  }

  @Test
  @DisplayName("193 AC-7 a consumed message's correlationId is in its log line and gone afterwards")
  void correlationIdIsInTheLogsWhileHandling(CapturedOutput output) {
    var correlationId = "corr-193-" + UUID.randomUUID();
    var with = UUID.randomUUID();
    var without = UUID.randomUUID();
    publish(EVENTS, "event.changed", with, eventChanged(with, UUID.randomUUID(), eventPayload("Draft", 1)),
        correlationId);
    publish(EVENTS, "event.changed", without, eventChanged(without, UUID.randomUUID(), eventPayload("Draft", 1)));

    await().atMost(WAIT).untilAsserted(() -> {
      assertThat(logLines(output)).anyMatch(line -> with.toString().equals(line.get("messageId")));
      assertThat(logLines(output)).anyMatch(line -> without.toString().equals(line.get("messageId")));
    });
    var lines = logLines(output);
    assertThat(lines.stream().filter(line -> with.toString().equals(line.get("messageId"))).toList())
        .singleElement().satisfies(line -> assertThat(line.get(Correlation.MDC_KEY)).isEqualTo(correlationId));
    assertThat(lines.stream().filter(line -> without.toString().equals(line.get("messageId"))).toList())
        .singleElement().satisfies(line -> assertThat(line).doesNotContainKey(Correlation.MDC_KEY));
  }
}
