package pt.isep.labdsof.authusers.adapters.out.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.transaction.support.TransactionTemplate;
import pt.isep.labdsof.authusers.Correlation;
import pt.isep.labdsof.authusers.Infrastructure;
import pt.isep.labdsof.authusers.IntegrationTest;
import pt.isep.labdsof.authusers.adapters.out.persistence.JdbcOutbox;
import pt.isep.labdsof.authusers.application.Identity;
import pt.isep.labdsof.authusers.application.MeService;
import pt.isep.labdsof.authusers.application.OutgoingEvent;
import tools.jackson.databind.json.JsonMapper;

/** AC-5, AC-6 and AC-8 (the publishing half): the outbox with real PostgreSQL and RabbitMQ (spec D1, D8). */
class OutboxIT extends IntegrationTest {

  static final String EVENTS_QUEUE = "events.auth-users-events";
  static final String REPORTS_QUEUE = "reports.auth-users-events";
  static final RabbitTemplate ADMIN = Infrastructure.adminTemplate();

  @Autowired MeService service;
  @Autowired JdbcOutbox outbox;
  @Autowired RabbitTemplate rabbit;
  @Autowired TransactionTemplate tx;
  @Autowired JsonMapper json;
  @Autowired ApplicationContext context;

  @BeforeEach
  void cleanSlate() {
    // Rows left by other test classes are not this test's concern; the broker queues start empty too.
    jdbc.sql("UPDATE outbox SET published_at = now() WHERE published_at IS NULL").update();
    var admin = new RabbitAdmin(ADMIN.getConnectionFactory());
    admin.purgeQueue(EVENTS_QUEUE, false);
    admin.purgeQueue(REPORTS_QUEUE, false);
  }

  @AfterEach
  void clearMdc() {
    MDC.clear();
  }

  OutboxPublisher publisher(RabbitTemplate template) {
    return new OutboxPublisher(outbox, template, tx, Clock.systemUTC(), 50, Duration.ofSeconds(2));
  }

  static Identity identity(UUID user) {
    return new Identity(user, user + "@farol.test", true, "Ana Silva");
  }

  UUID messageIdOf(UUID user, String type) {
    return jdbc.sql("SELECT message_id FROM outbox WHERE body->'payload'->>'userId' = :id AND type = :type")
        .param("id", user.toString()).param("type", type).query(UUID.class).single();
  }

  Map<String, Object> row(UUID messageId) {
    return jdbc.sql("SELECT attempts, next_attempt_at, published_at, last_error FROM outbox "
        + "WHERE message_id = :id").param("id", messageId).query().singleRow();
  }

  long outboxId(UUID messageId) {
    return jdbc.sql("SELECT id FROM outbox WHERE message_id = :id").param("id", messageId)
        .query(Long.class).single();
  }

  String storedBody(UUID messageId) {
    return jdbc.sql("SELECT body::text FROM outbox WHERE message_id = :id").param("id", messageId)
        .query(String.class).single();
  }

  /** Every message waiting in the queue, in delivery order. */
  List<Message> drain(String queue) {
    var messages = new ArrayList<Message>();
    Message message;
    while ((message = ADMIN.receive(queue, 500)) != null) {
      messages.add(message);
    }
    return messages;
  }

  static long count(List<Message> messages, UUID messageId) {
    return messages.stream().filter(m -> messageId.toString().equals(m.getMessageProperties().getMessageId()))
        .count();
  }

  static Message find(List<Message> messages, UUID messageId) {
    return messages.stream().filter(m -> messageId.toString().equals(m.getMessageProperties().getMessageId()))
        .findFirst().orElseThrow();
  }

  OutgoingEvent event(UUID messageId, String routingKey) {
    return new OutgoingEvent(messageId, OutgoingEvent.EXCHANGE, routingKey, "UserProfileChanged", 1,
        Instant.now(), null, "", Map.of("userId", UUID.randomUUID().toString(), "displayName", "Test",
            "version", 1));
  }

  UUID addEvent(String routingKey) {
    var messageId = UUID.randomUUID();
    tx.executeWithoutResult(status -> outbox.add(event(messageId, routingKey)));
    return messageId;
  }

  long unpublished() {
    return jdbc.sql("SELECT count(*) FROM outbox WHERE published_at IS NULL").query(Long.class).single();
  }

  // --- persistence --------------------------------------------------------------------------------------

  @Test
  @DisplayName("191 AC-5 due rows are claimed in id order up to the limit, skipping published and "
      + "not-yet-due rows")
  void claimsDueRows() {
    var a = addEvent("user-profile.changed");
    var b = addEvent("user-profile.changed");
    var c = addEvent("user-profile.changed");
    var d = UUID.randomUUID();
    var dEvent = event(d, "global-role.changed");
    MDC.put(Correlation.MDC_KEY, "c-27");
    tx.executeWithoutResult(status -> outbox.add(dEvent));
    MDC.remove(Correlation.MDC_KEY);
    jdbc.sql("UPDATE outbox SET published_at = now() WHERE message_id = :id").param("id", b).update();
    jdbc.sql("UPDATE outbox SET next_attempt_at = now() + interval '1 hour' WHERE message_id = :id")
        .param("id", c).update();
    jdbc.sql("UPDATE outbox SET attempts = 3 WHERE message_id = :id").param("id", d).update();

    var claimed = tx.execute(status -> List.of(outbox.claimDue(1), outbox.claimDue(10)));

    assertThat(claimed.get(0)).extracting(JdbcOutbox.Pending::messageId).containsExactly(a);
    var all = claimed.get(1);
    assertThat(all).extracting(JdbcOutbox.Pending::messageId).containsExactly(a, d);
    var first = all.get(0);
    var last = all.get(1);
    assertThat(first.correlationId()).isNull();
    assertThat(first.attempts()).isZero();
    assertThat(first.exchange()).isEqualTo("farol.auth-users");
    assertThat(first.routingKey()).isEqualTo("user-profile.changed");
    assertThat(first.type()).isEqualTo("UserProfileChanged");
    assertThat(last.correlationId()).isEqualTo("c-27");
    assertThat(last.attempts()).isEqualTo(3);
    assertThat(last.routingKey()).isEqualTo("global-role.changed");
    assertThat(json.readTree(last.body())).isEqualTo(json.valueToTree(dEvent.envelope()));
    assertThat(json.readTree(last.body())).isEqualTo(json.readTree(storedBody(d)));
  }

  @Test
  @DisplayName("191 AC-5 a published row clears its last error, a failed row keeps attempts, next attempt "
      + "and reason")
  void marksRows() {
    var messageId = addEvent("user-profile.changed");
    var id = outboxId(messageId);
    var next = Instant.now().plusSeconds(100).truncatedTo(ChronoUnit.MICROS);

    outbox.markFailed(id, 2, next, "x");

    var failed = row(messageId);
    assertThat(failed).containsEntry("attempts", 2).containsEntry("last_error", "x");
    assertThat(((Timestamp) failed.get("next_attempt_at")).toInstant()).isEqualTo(next);
    assertThat(failed.get("published_at")).isNull();

    var at = Instant.now().plusSeconds(5).truncatedTo(ChronoUnit.MICROS);
    outbox.markPublished(id, at);

    var published = row(messageId);
    assertThat(((Timestamp) published.get("published_at")).toInstant()).isEqualTo(at);
    assertThat(published.get("last_error")).isNull();
    assertThat(published).containsEntry("attempts", 2);
  }

  @Test
  @DisplayName("191 AC-5 a failure reason is stored without NUL characters and cut to 500 characters")
  void boundsTheReason() {
    var messageId = addEvent("user-profile.changed");
    var id = outboxId(messageId);
    var emoji = new String(Character.toChars(0x1F600));
    var reason = "\u0000" + "x".repeat(498) + emoji + "y".repeat(100);

    outbox.markFailed(id, 1, Instant.now(), reason);

    var stored = (String) row(messageId).get("last_error");
    assertThat(stored.codePointCount(0, stored.length())).isEqualTo(500);
    assertThat(stored).isEqualTo("�" + "x".repeat(498) + emoji);

    outbox.markFailed(id, 2, Instant.now(), "short reason");
    assertThat(row(messageId)).containsEntry("last_error", "short reason");
  }

  // --- publishing ---------------------------------------------------------------------------------------

  @Test
  @DisplayName("191 AC-5 a first GET /me use case call is published to farol.auth-users and lands in "
      + "events.auth-users-events")
  void firstCallIsPublished() {
    var user = UUID.randomUUID();
    service.me(identity(user));
    var messageId = messageIdOf(user, "UserProfileChanged");

    assertThat(publisher(rabbit).publishDue()).isEqualTo(1);

    var events = drain(EVENTS_QUEUE);
    var reports = drain(REPORTS_QUEUE);
    var stored = json.readTree(storedBody(messageId));
    for (var messages : List.of(events, reports)) {
      assertThat(count(messages, messageId)).isEqualTo(1);
      var message = find(messages, messageId);
      var properties = message.getMessageProperties();
      assertThat(properties.getType()).isEqualTo("UserProfileChanged");
      assertThat(properties.getAppId()).isEqualTo("auth-users");
      assertThat(properties.getContentType()).isEqualTo(MessageProperties.CONTENT_TYPE_JSON);
      assertThat(properties.getContentEncoding()).isEqualTo("UTF-8");
      assertThat(properties.getReceivedDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
      assertThat(properties.getReceivedExchange()).isEqualTo("farol.auth-users");
      assertThat(properties.getReceivedRoutingKey()).isEqualTo("user-profile.changed");
      assertThat(json.readTree(message.getBody())).isEqualTo(stored);
    }
    var published = row(messageId);
    assertThat(published.get("published_at")).isNotNull();
    assertThat(published).containsEntry("attempts", 0);
    assertThat(published.get("last_error")).isNull();
  }

  @Test
  @DisplayName("191 AC-8 the outbox row's correlation id is the AMQP correlationId of the published "
      + "message, and none without one")
  void correlationIdReachesTheBroker() {
    var withId = UUID.randomUUID();
    var without = UUID.randomUUID();
    MDC.put(Correlation.MDC_KEY, "c-17");
    service.me(identity(withId));
    MDC.remove(Correlation.MDC_KEY);
    service.me(identity(without));

    assertThat(publisher(rabbit).publishDue()).isEqualTo(2);

    var events = drain(EVENTS_QUEUE);
    assertThat(find(events, messageIdOf(withId, "UserProfileChanged")).getMessageProperties()
        .getCorrelationId()).isEqualTo("c-17");
    assertThat(find(events, messageIdOf(without, "UserProfileChanged")).getMessageProperties()
        .getCorrelationId()).isNull();
  }

  @Test
  @DisplayName("191 AC-6 the bootstrap's GlobalRoleChanged is published after its UserProfileChanged")
  void bootstrapRoleIsPublishedAfterTheProfile() {
    var user = UUID.randomUUID();
    service.me(new Identity(user, Infrastructure.BOOTSTRAP_EMAIL, true, "Admin"));
    var profile = messageIdOf(user, "UserProfileChanged");
    var role = messageIdOf(user, "GlobalRoleChanged");

    assertThat(publisher(rabbit).publishDue()).isEqualTo(2);

    var events = drain(EVENTS_QUEUE);
    assertThat(events).extracting(m -> m.getMessageProperties().getMessageId())
        .containsExactly(profile.toString(), role.toString());
    assertThat(events.get(1).getMessageProperties().getReceivedRoutingKey()).isEqualTo("global-role.changed");
    assertThat(events.get(1).getMessageProperties().getType()).isEqualTo("GlobalRoleChanged");
  }

  @Test
  @DisplayName("191 AC-5 a message waits while the broker is down and is sent once it returns")
  void waitsWhileTheBrokerIsDown() {
    var first = addEvent("user-profile.changed");
    var second = addEvent("user-profile.changed");
    var unreachable = new CachingConnectionFactory("127.0.0.1", 1);
    try {
      var before = Instant.now();
      var down = publisher(new RabbitTemplate(unreachable));
      assertThat(down.publishDue()).isZero();

      var failed = row(first);
      assertThat(failed.get("published_at")).isNull();
      assertThat(failed).containsEntry("attempts", 1);
      assertThat((String) failed.get("last_error")).isNotBlank();
      assertThat(((Timestamp) failed.get("next_attempt_at")).toInstant()).isAfterOrEqualTo(before.plusSeconds(2));
      assertThat(row(second)).containsEntry("attempts", 0);

      assertThat(down.publishDue()).isZero(); // paused: nothing is tried or changed
      assertThat(row(first)).containsEntry("attempts", 1);
      assertThat(row(second)).containsEntry("attempts", 0);

      jdbc.sql("UPDATE outbox SET next_attempt_at = now() WHERE message_id = :id").param("id", first).update();
      assertThat(publisher(rabbit).publishDue()).isEqualTo(2);

      assertThat(row(first)).containsEntry("attempts", 1);
      assertThat(row(first).get("last_error")).isNull();
      assertThat(row(first).get("published_at")).isNotNull();
      assertThat(row(second).get("published_at")).isNotNull();
      var events = drain(EVENTS_QUEUE);
      assertThat(events).extracting(m -> m.getMessageProperties().getMessageId())
          .containsExactly(first.toString(), second.toString());
    } finally {
      unreachable.destroy();
    }
  }

  @Test
  @DisplayName("191 AC-5 an unroutable message stays in the outbox and does not hold back the next one")
  void unroutableDoesNotBlock() {
    var refused = addEvent("nothing.bound");
    try {
      var next = addEvent("user-profile.changed");

      assertThat(publisher(rabbit).publishDue()).isEqualTo(1);

      assertThat(row(refused).get("published_at")).isNull();
      assertThat(row(refused)).containsEntry("attempts", 1);
      assertThat((String) row(refused).get("last_error")).contains("unroutable");
      assertThat(row(next).get("published_at")).isNotNull();
      assertThat(count(drain(EVENTS_QUEUE), next)).isEqualTo(1);
    } finally {
      jdbc.sql("DELETE FROM outbox WHERE message_id = :id").param("id", refused).update();
    }
  }

  @Test
  @DisplayName("191 AC-5 a crash between save and publish sends the message once after restart")
  void crashBetweenSaveAndPublish() {
    // The shared context has no publisher: the call is committed and the service "crashes" before publishing.
    var user = UUID.randomUUID();
    service.me(identity(user));
    var messageId = messageIdOf(user, "UserProfileChanged");
    assertThat(row(messageId).get("published_at")).isNull();

    // The restarted service, now with its publisher running (the other values come from application.yaml).
    try (var restarted = Infrastructure.startAuthUsers(
        Map.of("farol.outbox.enabled", "true", "farol.outbox.poll-interval", "100ms"))) {
      await().atMost(Duration.ofSeconds(30)).until(() -> row(messageId).get("published_at") != null);
      // Several more runs (every 100 ms) before the queue is read: none may send it again.
      await().pollDelay(Duration.ofSeconds(1)).atMost(Duration.ofSeconds(3)).until(() -> true);
    }

    var events = drain(EVENTS_QUEUE);
    assertThat(count(events, messageId)).isEqualTo(1);
    assertThat(find(events, messageId).getMessageProperties().getType()).isEqualTo("UserProfileChanged");
  }

  @Test
  @DisplayName("191 AC-5 two publishers never claim the same row")
  void skipLocked() throws Exception {
    var first = addEvent("user-profile.changed");
    var second = addEvent("user-profile.changed");
    var locked = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    try (var pool = Executors.newSingleThreadExecutor()) {
      var holder = pool.submit(() -> tx.execute(status -> {
        var claimed = outbox.claimDue(1);
        locked.countDown();
        try {
          release.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
        return claimed;
      }));
      assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
      var other = tx.execute(status -> outbox.claimDue(50));
      release.countDown();
      var held = holder.get(10, TimeUnit.SECONDS);

      assertThat(held).extracting(JdbcOutbox.Pending::messageId).containsExactly(first);
      assertThat(other).extracting(JdbcOutbox.Pending::messageId).containsExactly(second);
    }
  }

  @Test
  @DisplayName("191 AC-5 two publishers at once send every message exactly once")
  void twoPublishersSendEachMessageOnce() throws Exception {
    var ids = new ArrayList<UUID>();
    for (int i = 0; i < 20; i++) {
      ids.add(addEvent("user-profile.changed"));
    }
    var start = new CountDownLatch(1);
    var deadline = Instant.now().plusSeconds(30);
    int sent = 0;
    try (var pool = Executors.newFixedThreadPool(2)) {
      var runs = new ArrayList<Future<Integer>>();
      for (int i = 0; i < 2; i++) {
        var publisher = new OutboxPublisher(outbox, rabbit, tx, Clock.systemUTC(), 3, Duration.ofSeconds(2));
        runs.add(pool.submit(() -> {
          start.await();
          int total = 0;
          while (unpublished() > 0 && Instant.now().isBefore(deadline)) {
            total += publisher.publishDue();
          }
          return total;
        }));
      }
      start.countDown();
      for (var run : runs) {
        sent += run.get(40, TimeUnit.SECONDS);
      }
    }

    assertThat(sent).isEqualTo(20);
    assertThat(unpublished()).isZero();
    var events = drain(EVENTS_QUEUE);
    assertThat(events).hasSize(20);
    for (var id : ids) {
      assertThat(count(events, id)).isEqualTo(1);
    }
  }

  @Test
  @DisplayName("191 AC-5 a publish the auth-users user may not write is refused and the row is kept")
  void refusedWithoutWritePermission() {
    var messageId = addEvent("user-profile.changed");
    var fresh = Infrastructure.authUsersTemplate();
    try {
      Infrastructure.rabbitmqctl("set_permissions", "-p", "/", "auth-users", "^$", "^farol\\.dead-letters$",
          "^auth-users\\..*");

      assertThat(publisher(fresh).publishDue()).isZero();

      var failed = row(messageId);
      assertThat(failed.get("published_at")).isNull();
      assertThat(failed).containsEntry("attempts", 1);
      // The broker closes the channel on the refused publish; the client reports the pending confirm as a nack
      assertThat((String) failed.get("last_error")).startsWith("not confirmed: ");
      assertThat(drain(EVENTS_QUEUE)).isEmpty();
    } finally {
      ((CachingConnectionFactory) fresh.getConnectionFactory()).destroy();
      Infrastructure.setUpBroker();
    }
  }

  @Test
  @DisplayName("191 AC-5 Auth & Users declares nothing on the broker")
  void declaresNothing() {
    assertThat(context.getBeansOfType(Declarable.class)).isEmpty();
  }

  @Test
  @DisplayName("191 AC-5 the service starts before its broker user exists and publishes once the user is "
      + "created")
  void startsBeforeTheBrokerUser() throws Exception {
    var user = UUID.randomUUID();
    var token = JWKS.valid(UUID.randomUUID().toString());
    try {
      Infrastructure.rabbitmqctl("delete_user", "auth-users");
      service.me(identity(user));
      var messageId = messageIdOf(user, "UserProfileChanged");

      try (var app = Infrastructure.startAuthUsers(
          Map.of("farol.outbox.enabled", "true", "farol.outbox.poll-interval", "100ms"));
          var client = HttpClient.newHttpClient()) {
        assertThat(app.isRunning()).isTrue();
        var statusPort = app.getEnvironment().getRequiredProperty("local.server.port", Integer.class);
        var status = HttpRequest.newBuilder().uri(URI.create("http://127.0.0.1:" + statusPort + "/api/v1/status"))
            .header("Authorization", "Bearer " + token).timeout(Duration.ofSeconds(10)).GET().build();
        assertThat(client.send(status, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(200);

        await().atMost(Duration.ofSeconds(30)).until(() -> (int) row(messageId).get("attempts") >= 1);
        var failed = row(messageId);
        assertThat(failed.get("published_at")).isNull();
        assertThat((String) failed.get("last_error")).isNotBlank();
        assertThat(client.send(status, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(200);

        Infrastructure.setUpBroker();

        await().atMost(Duration.ofSeconds(30)).until(() -> row(messageId).get("published_at") != null);
      }
      assertThat(count(drain(EVENTS_QUEUE), messageId)).isEqualTo(1);
    } finally {
      Infrastructure.setUpBroker();
    }
  }
}
