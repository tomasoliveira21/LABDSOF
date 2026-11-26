package pt.isep.labdsof.events;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.Arrays;
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
import pt.isep.labdsof.events.domain.Membership;

/** MembershipChanged reaches the membership copy through the real broker as the events user (spec D2, D3). */
@ExtendWith(OutputCaptureExtension.class)
class LocalCopiesIT extends IntegrationTest {

  static final Duration WAIT = Duration.ofSeconds(15);
  static final String DLQ = "events.auth-users-events.dlq";

  final RabbitTemplate admin = Infrastructure.adminTemplate();
  final UUID event = UUID.randomUUID();
  final UUID user = UUID.randomUUID();

  static String envelope(UUID messageId, String type, UUID eventId, String payload) {
    return """
        {"messageId":"%s","type":"%s","schemaVersion":1,"occurredAt":"2027-05-08T23:14:05Z",
         "producer":"auth-users","eventId":%s,"actorUserId":"","payload":%s}
        """.formatted(messageId, type, eventId == null ? "null" : "\"" + eventId + "\"", payload);
  }

  String membership(String role, String status, long version, UUID... teams) {
    var ids = String.join(",", Arrays.stream(teams).map(t -> "\"" + t + "\"").toList());
    var statusField = status == null ? "" : "\"status\":\"" + status + "\",";
    return "{\"userId\":\"%s\",\"role\":\"%s\",%s\"teamIds\":[%s],\"version\":%d}"
        .formatted(user, role, statusField, ids, version);
  }

  void publish(String routingKey, UUID messageId, String body, String correlationId) {
    var properties = new MessageProperties();
    properties.setMessageId(messageId.toString());
    properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
    properties.setCorrelationId(correlationId);
    admin.send("farol.auth-users", routingKey, new Message(body.getBytes(UTF_8), properties));
  }

  void publish(String routingKey, UUID messageId, String body) {
    publish(routingKey, messageId, body, null);
  }

  void membershipChanged(UUID messageId, String payload) {
    publish("membership.changed", messageId, envelope(messageId, "MembershipChanged", event, payload));
  }

  Optional<Membership> copy() {
    return copies.membership(event, user);
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

  /** A valid message, awaited in processed_message: everything sent before it has been handled. */
  void awaitQueueMovedOn() {
    var marker = UUID.randomUUID();
    publish("membership.changed", marker, envelope(marker, "MembershipChanged", UUID.randomUUID(),
        membership("RegularUser", "Active", 1)));
    await().atMost(WAIT).until(() -> processed(marker) == 1);
  }

  @Test
  @DisplayName("192 AC-6 a MembershipChanged creates and updates the copy")
  void membershipApplied() {
    var team = UUID.randomUUID();
    membershipChanged(UUID.randomUUID(), membership("RegularUser", "Active", 1));
    await().atMost(WAIT).until(() -> copy().isPresent());
    assertThat(copy().orElseThrow().role()).isEqualTo(Membership.Role.RegularUser);
    assertThat(copy().orElseThrow().teamIds()).isEmpty();

    membershipChanged(UUID.randomUUID(), membership("EventAdmin", "Active", 2, team));
    await().atMost(WAIT).until(() -> copy().orElseThrow().version() == 2);

    assertThat(copy().orElseThrow().role()).isEqualTo(Membership.Role.EventAdmin);
    assertThat(copy().orElseThrow().teamIds()).containsExactly(team);
  }

  @Test
  @DisplayName("192 AC-6 a MembershipChanged without status is stored as Active")
  void missingStatusIsActive() {
    membershipChanged(UUID.randomUUID(), membership("RegularUser", null, 1));

    await().atMost(WAIT).until(() -> copy().isPresent());

    assertThat(copy().orElseThrow().status()).isEqualTo(Membership.Status.Active);
    assertThat(copy().orElseThrow().isActive()).isTrue();
  }

  @Test
  @DisplayName("192 AC-6 a duplicate MembershipChanged is applied once")
  void duplicate() {
    var messageId = UUID.randomUUID();
    membershipChanged(messageId, membership("RegularUser", "Active", 1));
    await().atMost(WAIT).until(() -> processed(messageId) == 1);
    // Redelivery of the same message (for instance the producer crashed after the broker's confirm)
    membershipChanged(messageId, membership("EventAdmin", "Active", 5));
    awaitQueueMovedOn();

    assertThat(copy().orElseThrow().role()).isEqualTo(Membership.Role.RegularUser);
    assertThat(copy().orElseThrow().version()).isEqualTo(1);
    assertThat(processed(messageId)).isEqualTo(1);
  }

  @Test
  @DisplayName("192 AC-6 an older or equal MembershipChanged version changes nothing")
  void olderOrEqualMembership() {
    var newer = UUID.randomUUID();
    membershipChanged(newer, membership("EventAdmin", "Active", 2));
    await().atMost(WAIT).until(() -> processed(newer) == 1);
    var older = UUID.randomUUID();
    var equal = UUID.randomUUID();
    membershipChanged(older, membership("RegularUser", "Active", 1));
    membershipChanged(equal, membership("RegularUser", "Removed", 2));
    await().atMost(WAIT).until(() -> processed(older) == 1 && processed(equal) == 1);

    assertThat(copy().orElseThrow().role()).isEqualTo(Membership.Role.EventAdmin);
    assertThat(copy().orElseThrow().status()).isEqualTo(Membership.Status.Active);
    assertThat(copy().orElseThrow().version()).isEqualTo(2);
  }

  @Test
  @DisplayName("192 AC-6 a Removed membership is kept and a late older Active version does not bring it back")
  void removedStaysRemoved() {
    var removed = UUID.randomUUID();
    membershipChanged(removed, membership("RegularUser", "Removed", 3));
    await().atMost(WAIT).until(() -> processed(removed) == 1);
    var late = UUID.randomUUID();
    membershipChanged(late, membership("RegularUser", "Active", 2));
    await().atMost(WAIT).until(() -> processed(late) == 1);

    assertThat(copy()).get().satisfies(m -> {
      assertThat(m.status()).isEqualTo(Membership.Status.Removed);
      assertThat(m.version()).isEqualTo(3);
    });
  }

  private static String body(String... fields) {
    return "{" + String.join(",", Arrays.stream(fields).filter(f -> f != null).toList()) + "}";
  }

  private static Stream<Arguments> invalidMessages() {
    var message = "\"messageId\":\"%1$s\"";
    var type = "\"type\":\"MembershipChanged\"";
    var schema = "\"schemaVersion\":1";
    var occurred = "\"occurredAt\":\"2027-05-08T23:14:05Z\"";
    var producer = "\"producer\":\"auth-users\"";
    var actor = "\"actorUserId\":\"\"";
    var eventId = "\"eventId\":\"%2$s\"";
    var user = "\"userId\":\"11111111-2222-3333-4444-555555555555\"";
    var role = "\"role\":\"RegularUser\"";
    var status = "\"status\":\"Active\"";
    var teams = "\"teamIds\":[]";
    var version = "\"version\":1";
    var payload = "\"payload\":" + body(user, role, status, teams, version);
    return Stream.of(
        Arguments.of("bad JSON", "not json"),
        Arguments.of("schemaVersion 2", body(message, type, "\"schemaVersion\":2", occurred, producer, actor,
            eventId, payload)),
        Arguments.of("no schemaVersion", body(message, type, occurred, producer, actor, eventId, payload)),
        Arguments.of("no messageId", body(type, schema, occurred, producer, actor, eventId, payload)),
        Arguments.of("no type", body(message, schema, occurred, producer, actor, eventId, payload)),
        Arguments.of("no occurredAt", body(message, type, schema, producer, actor, eventId, payload)),
        Arguments.of("occurredAt not a date", body(message, type, schema, "\"occurredAt\":\"yesterday\"",
            producer, actor, eventId, payload)),
        Arguments.of("no producer", body(message, type, schema, occurred, actor, eventId, payload)),
        Arguments.of("no actorUserId", body(message, type, schema, occurred, producer, eventId, payload)),
        Arguments.of("no eventId", body(message, type, schema, occurred, producer, actor, payload)),
        Arguments.of("eventId not a UUID", body(message, type, schema, occurred, producer, actor,
            "\"eventId\":\"x\"", payload)),
        Arguments.of("no payload", body(message, type, schema, occurred, producer, actor, eventId)),
        Arguments.of("unknown role", envelopeWith(message, type, schema, occurred, producer, actor, eventId,
            body(user, "\"role\":\"Owner\"", status, teams, version))),
        Arguments.of("no role", envelopeWith(message, type, schema, occurred, producer, actor, eventId,
            body(user, status, teams, version))),
        Arguments.of("unknown status", envelopeWith(message, type, schema, occurred, producer, actor, eventId,
            body(user, role, "\"status\":\"Gone\"", teams, version))),
        Arguments.of("userId not a UUID", envelopeWith(message, type, schema, occurred, producer, actor,
            eventId, body("\"userId\":\"ana\"", role, status, teams, version))),
        Arguments.of("no userId", envelopeWith(message, type, schema, occurred, producer, actor, eventId,
            body(role, status, teams, version))),
        Arguments.of("no teamIds", envelopeWith(message, type, schema, occurred, producer, actor, eventId,
            body(user, role, status, version))),
        Arguments.of("a null team id", envelopeWith(message, type, schema, occurred, producer, actor, eventId,
            body(user, role, status, "\"teamIds\":[null]", version))),
        Arguments.of("no version", envelopeWith(message, type, schema, occurred, producer, actor, eventId,
            body(user, role, status, teams))),
        Arguments.of("version 0", envelopeWith(message, type, schema, occurred, producer, actor, eventId,
            body(user, role, status, teams, "\"version\":0"))));
  }

  private static String envelopeWith(String message, String type, String schema, String occurred,
      String producer, String actor, String eventId, String payload) {
    return body(message, type, schema, occurred, producer, actor, eventId, "\"payload\":" + payload);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("invalidMessages")
  @DisplayName("192 AC-6 an invalid MembershipChanged goes to the dead-letter queue with its reason and is "
      + "not retried")
  void invalid(String name, String template) {
    var messageId = UUID.randomUUID();
    var body = template.formatted(messageId, event);
    publish("membership.changed", messageId, body);

    var deadLetter = await().atMost(WAIT).until(() -> receive(DLQ, messageId), Optional::isPresent)
        .orElseThrow();
    assertThat(new String(deadLetter.getBody(), UTF_8)).isEqualTo(body);
    assertThat(deadLetter.getMessageProperties().<String>getHeader("x-farol-error")).matches("(?s)^[A-Za-z]+: .*");
    assertThat(deadLetter.getMessageProperties().getReceivedDeliveryMode())
        .isEqualTo(MessageDeliveryMode.PERSISTENT);
    assertThat(processed(messageId)).isZero();

    // Not retried: once the queue has moved on, no second dead letter of this message exists
    awaitQueueMovedOn();
    assertThat(receive(DLQ, messageId)).isEmpty();
  }

  @Test
  @DisplayName("192 AC-6 a database failure is retried 4 times, then the message goes to the dead-letter queue")
  void databaseFailureIsRetriedThenDeadLettered() {
    var n = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    var messageId = UUID.randomUUID();
    jdbc.sql("CREATE SEQUENCE test_attempts_" + n).update();
    jdbc.sql("CREATE FUNCTION test_fail_" + n + "() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN "
        + "PERFORM nextval('test_attempts_" + n + "'); RAISE EXCEPTION 'test: database unavailable'; END $$")
        .update();
    jdbc.sql("CREATE TRIGGER test_fail_" + n + " BEFORE INSERT OR UPDATE ON membership_copy FOR EACH ROW "
        + "WHEN (NEW.user_id = '" + user + "') EXECUTE FUNCTION test_fail_" + n + "()").update();
    try {
      membershipChanged(messageId, membership("RegularUser", "Active", 1));

      var deadLetter = await().atMost(WAIT).until(() -> receive(DLQ, messageId), Optional::isPresent)
          .orElseThrow();

      assertThat(deadLetter.getMessageProperties().<String>getHeader("x-farol-error"))
          .contains("database unavailable");
      assertThat(jdbc.sql("SELECT last_value FROM test_attempts_" + n).query(Long.class).single()).isEqualTo(4L);
      assertThat(processed(messageId)).isZero();
      assertThat(copy()).isEmpty();
    } finally {
      jdbc.sql("DROP TRIGGER IF EXISTS test_fail_" + n + " ON membership_copy").update();
      jdbc.sql("DROP FUNCTION IF EXISTS test_fail_" + n + "()").update();
      jdbc.sql("DROP SEQUENCE IF EXISTS test_attempts_" + n).update();
    }
  }

  @ParameterizedTest(name = "{0}")
  @CsvSource({"UserProfileChanged,user-profile.changed",
      "UserStatusChanged,user-status.changed", "UserDeleted,user.deleted", "SomethingNew,membership.changed"})
  @DisplayName("192 AC-6 the other Auth & Users events and unknown types are acknowledged and logged, not "
      + "stored (GlobalRoleChanged left this list in US-06: it is handled now)")
  void otherTypesAreAcknowledged(String type, String routingKey, CapturedOutput output) {
    var messageId = UUID.randomUUID();
    publish(routingKey, messageId, envelope(messageId, type, null,
        "{\"userId\":\"u\",\"displayName\":\"Ana-Test-Name\"}"));

    await().atMost(WAIT).untilAsserted(() -> assertThat(logLines(output)).anyMatch(line ->
        messageId.toString().equals(line.get("messageId")) && type.equals(line.get("type"))
            && "INFO".equals(line.get("level"))));

    awaitQueueMovedOn();
    assertThat(processed(messageId)).isZero();
    assertThat(receive(DLQ, messageId)).isEmpty();
    assertThat(output.getAll()).doesNotContain("Ana-Test-Name");
  }

  /** Compose starts Events and rabbitmq-setup together, so the broker may refuse its user at first (D2). */
  @Test
  @DisplayName("192 AC-6 Events starts before its broker user exists and consumes once it is created")
  void startsBeforeBrokerUser() {
    var late = "events-" + UUID.randomUUID();
    try (var events = Infrastructure.startEvents(
        Map.of("spring.rabbitmq.username", late, "spring.rabbitmq.password", "test-only-late"))) {
      assertThat(events.isRunning()).isTrue();
      Infrastructure.rabbitmqctl("add_user", late, "test-only-late");
      Infrastructure.rabbitmqctl("set_permissions", late, "^$", "^(farol\\.events|farol\\.dead-letters)$",
          "^events\\..*");
      await().atMost(Duration.ofSeconds(30)).until(() ->
          Infrastructure.rabbitmqctl("list_channels", "user", "consumer_count")
              .lines().filter(line -> line.startsWith(late + "\t")).anyMatch(line -> !line.endsWith("\t0")));
    }
    // Only after the context has closed its connections: deleting a connected user makes the broker cut them
    Infrastructure.rabbitmqctl("delete_user", late);
  }

  @Test
  @DisplayName("192 AC-6 Events starts while its queue is missing and consumes once it exists")
  void startsWhileTheQueueIsMissing() {
    var vhost = "events-missing-" + UUID.randomUUID();
    Infrastructure.rabbitmqctl("add_vhost", vhost);
    var connection = new CachingConnectionFactory(Infrastructure.RABBIT.getHost(),
        Infrastructure.RABBIT.getAmqpPort());
    try {
      Infrastructure.rabbitmqctl("set_permissions", "-p", vhost, "events", "^$",
          "^(farol\\.events|farol\\.dead-letters)$", "^events\\..*");
      Infrastructure.rabbitmqctl("set_permissions", "-p", vhost, Infrastructure.RABBIT.getAdminUsername(),
          ".*", ".*", ".*");
      try (var events = Infrastructure.startEvents(Map.of("spring.rabbitmq.virtual-host", vhost))) {
        assertThat(events.isRunning()).isTrue();

        connection.setUsername(Infrastructure.RABBIT.getAdminUsername());
        connection.setPassword(Infrastructure.RABBIT.getAdminPassword());
        connection.setVirtualHost(vhost);
        new RabbitAdmin(connection).declareQueue(new Queue("events.auth-users-events", true));

        await().atMost(Duration.ofSeconds(60)).until(() ->
            Infrastructure.rabbitmqctl("list_queues", "-p", vhost, "name", "consumers").lines()
                .anyMatch(line -> line.startsWith("events.auth-users-events\t") && !line.endsWith("\t0")));
      }
    } finally {
      connection.destroy();
      Infrastructure.rabbitmqctl("delete_vhost", vhost);
    }
  }

  @Test
  @DisplayName("192 AC-8 the AMQP correlationId is in the logs while a message is handled and gone afterwards")
  void correlationIdIsInTheLogsWhileHandling(CapturedOutput output) {
    var correlationId = "corr-192-f01-" + UUID.randomUUID();
    var with = UUID.randomUUID();
    var without = UUID.randomUUID();
    var payload = "{\"userId\":\"u\"}";
    publish("user-profile.changed", with, envelope(with, "UserProfileChanged", null, payload), correlationId);
    publish("user-profile.changed", without, envelope(without, "UserProfileChanged", null, payload));

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
