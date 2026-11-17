package pt.isep.labdsof.events.adapters.out.messaging;

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
import pt.isep.labdsof.events.Correlation;
import pt.isep.labdsof.events.Infrastructure;
import pt.isep.labdsof.events.IntegrationTest;
import pt.isep.labdsof.events.adapters.out.persistence.JdbcOutbox;
import pt.isep.labdsof.events.application.OutgoingEvent;
import tools.jackson.databind.json.JsonMapper;

/** AC-7 and AC-8 (the writing and AMQP halves): the outbox with real PostgreSQL and RabbitMQ (spec D1). */
class OutboxIT extends IntegrationTest {

  static final String QUEUE = "auth-users.events-events";
  static final List<String> EVENT_CHANGED_QUEUES = List.of("auth-users.events-events", "reports.events-events",
      "transports.events-events", "badges.events-events");
  static final RabbitTemplate ADMIN = Infrastructure.adminTemplate();

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
    EVENT_CHANGED_QUEUES.forEach(queue -> admin.purgeQueue(queue, false));
  }

  @AfterEach
  void clearMdc() {
    MDC.clear();
  }

  OutboxPublisher publisher(RabbitTemplate template) {
    return new OutboxPublisher(outbox, template, tx, Clock.systemUTC(), 50, Duration.ofSeconds(2));
  }

  Map<String, Object> row(UUID messageId) {
    return jdbc.sql("SELECT attempts, next_attempt_at, published_at, last_error, correlation_id FROM outbox "
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

  OutgoingEvent event(UUID messageId, String routingKey, String type) {
    return new OutgoingEvent(messageId, OutgoingEvent.EXCHANGE, routingKey, type, 1, Instant.now(),
        UUID.randomUUID(), "", Map.of("name", "Queima das Fitas 2027", "status", "Published", "version", 1));
  }

  UUID addEvent(String routingKey) {
    var messageId = UUID.randomUUID();
    var type = "team.changed".equals(routingKey) ? "TeamChanged" : "EventChanged";
    tx.executeWithoutResult(status -> outbox.add(event(messageId, routingKey, type)));
    return messageId;
  }

  long unpublished() {
    return jdbc.sql("SELECT count(*) FROM outbox WHERE published_at IS NULL").query(Long.class).single();
  }

  // --- persistence --------------------------------------------------------------------------------------

  @Test
  @DisplayName("192 AC-7 an added event is stored as its §7.1 envelope from producer events, unpublished and "
      + "due at once")
  void addStoresTheEnvelope() {
    var messageId = UUID.randomUUID();
    var event = event(messageId, "event.changed", "EventChanged");

    tx.executeWithoutResult(status -> outbox.add(event));

    var stored = jdbc.sql("""
        SELECT exchange, routing_key, type, body::text AS body, body->>'producer' AS producer,
               body->>'eventId' AS event_id, attempts, published_at, last_error,
               next_attempt_at <= now() AS due
        FROM outbox WHERE message_id = :id
        """).param("id", messageId).query().singleRow();
    assertThat(stored).containsEntry("exchange", "farol.events")
        .containsEntry("routing_key", "event.changed")
        .containsEntry("type", "EventChanged")
        .containsEntry("producer", "events")
        .containsEntry("event_id", event.eventId().toString())
        .containsEntry("attempts", 0)
        .containsEntry("due", true);
    assertThat(stored.get("published_at")).isNull();
    assertThat(stored.get("last_error")).isNull();
    assertThat(json.readTree((String) stored.get("body"))).isEqualTo(json.valueToTree(event.envelope()));
  }

  @Test
  @DisplayName("192 AC-7 due rows are claimed in id order up to the limit, skipping published and "
      + "not-yet-due rows")
  void claimsDueRows() {
    var a = addEvent("event.changed");
    var b = addEvent("event.changed");
    var c = addEvent("event.changed");
    var d = UUID.randomUUID();
    var dEvent = event(d, "team.changed", "TeamChanged");
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
    assertThat(first.exchange()).isEqualTo("farol.events");
    assertThat(first.routingKey()).isEqualTo("event.changed");
    assertThat(first.type()).isEqualTo("EventChanged");
    assertThat(last.correlationId()).isEqualTo("c-27");
    assertThat(last.attempts()).isEqualTo(3);
    assertThat(last.exchange()).isEqualTo("farol.events");
    assertThat(last.routingKey()).isEqualTo("team.changed");
    assertThat(last.type()).isEqualTo("TeamChanged");
    assertThat(json.readTree(last.body())).isEqualTo(json.valueToTree(dEvent.envelope()));
    assertThat(json.readTree(last.body())).isEqualTo(json.readTree(storedBody(d)));
  }

  @Test
  @DisplayName("192 AC-7 a published row clears its last error, a failed row keeps attempts, next attempt "
      + "and reason")
  void marksRows() {
    var messageId = addEvent("event.changed");
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
  @DisplayName("192 AC-7 a failure reason is stored without NUL characters and cut to 500 characters")
  void boundsTheReason() {
    var messageId = addEvent("event.changed");
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
  @DisplayName("192 AC-7 an outbox row is published to farol.events with confirms and lands in every queue "
      + "bound to its routing key")
  void rowIsPublished() {
    var messageId = addEvent("event.changed");

    assertThat(publisher(rabbit).publishDue()).isEqualTo(1);

    var stored = json.readTree(storedBody(messageId));
    assertThat(stored.get("producer").asString()).isEqualTo("events");
    for (var queue : EVENT_CHANGED_QUEUES) {
      var messages = drain(queue);
      assertThat(count(messages, messageId)).as(queue).isEqualTo(1);
      var message = find(messages, messageId);
      var properties = message.getMessageProperties();
      assertThat(properties.getType()).isEqualTo("EventChanged");
      assertThat(properties.getAppId()).isEqualTo("events");
      assertThat(properties.getContentType()).isEqualTo(MessageProperties.CONTENT_TYPE_JSON);
      assertThat(properties.getContentEncoding()).isEqualTo("UTF-8");
      assertThat(properties.getReceivedDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
      assertThat(properties.getReceivedExchange()).isEqualTo("farol.events");
      assertThat(properties.getReceivedRoutingKey()).isEqualTo("event.changed");
      assertThat(json.readTree(message.getBody())).isEqualTo(stored);
    }
    var published = row(messageId);
    assertThat(published.get("published_at")).isNotNull();
    assertThat(published).containsEntry("attempts", 0);
    assertThat(published.get("last_error")).isNull();
  }

  @Test
  @DisplayName("192 AC-8 the outbox row's correlation id is the AMQP correlationId of the published "
      + "message, and none without one")
  void correlationIdReachesTheBroker() {
    MDC.put(Correlation.MDC_KEY, "c-24");
    var withId = addEvent("event.changed");
    MDC.remove(Correlation.MDC_KEY);
    var without = addEvent("event.changed");
    assertThat(row(withId)).containsEntry("correlation_id", "c-24");
    assertThat(row(without).get("correlation_id")).isNull();

    assertThat(publisher(rabbit).publishDue()).isEqualTo(2);

    var messages = drain(QUEUE);
    assertThat(find(messages, withId).getMessageProperties().getCorrelationId()).isEqualTo("c-24");
    assertThat(find(messages, without).getMessageProperties().getCorrelationId()).isNull();
  }

  @Test
  @DisplayName("192 AC-7 a message waits while the broker is down and is sent once it returns")
  void waitsWhileTheBrokerIsDown() {
    var first = addEvent("event.changed");
    var second = addEvent("event.changed");
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
      var messages = drain(QUEUE);
      assertThat(messages).extracting(m -> m.getMessageProperties().getMessageId())
          .containsExactly(first.toString(), second.toString());
    } finally {
      unreachable.destroy();
    }
  }

  @Test
  @DisplayName("192 AC-7 an unroutable message stays in the outbox and does not hold back the next one")
  void unroutableDoesNotBlock() {
    var refused = addEvent("nothing.bound");
    try {
      var next = addEvent("event.changed");

      assertThat(publisher(rabbit).publishDue()).isEqualTo(1);

      assertThat(row(refused).get("published_at")).isNull();
      assertThat(row(refused)).containsEntry("attempts", 1);
      assertThat((String) row(refused).get("last_error")).contains("unroutable");
      assertThat(row(next).get("published_at")).isNotNull();
      assertThat(count(drain(QUEUE), next)).isEqualTo(1);
    } finally {
      jdbc.sql("DELETE FROM outbox WHERE message_id = :id").param("id", refused).update();
    }
  }

  @Test
  @DisplayName("192 AC-7 two publishers never claim the same row")
  void skipLocked() throws Exception {
    var first = addEvent("event.changed");
    var second = addEvent("event.changed");
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
  @DisplayName("192 AC-7 two publishers at once send every message exactly once")
  void twoPublishersSendEachMessageOnce() throws Exception {
    var ids = new ArrayList<UUID>();
    for (int i = 0; i < 20; i++) {
      ids.add(addEvent("event.changed"));
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
    var messages = drain(QUEUE);
    assertThat(messages).hasSize(20);
    for (var id : ids) {
      assertThat(count(messages, id)).isEqualTo(1);
    }
  }

  @Test
  @DisplayName("192 AC-7 Events declares nothing on the broker")
  void declaresNothing() {
    assertThat(context.getBeansOfType(Declarable.class)).isEmpty();
  }

  // --- whole processes and broker users -----------------------------------------------------------------

  @Test
  @DisplayName("192 AC-7 a crash between save and publish sends the message once after restart")
  void crashBetweenSaveAndPublish() {
    // The shared context has no publisher: the row is committed and the service "crashes" before publishing.
    var messageId = addEvent("event.changed");
    assertThat(row(messageId).get("published_at")).isNull();

    // The restarted service, now with its publisher running (the other values come from application.yaml).
    try (var restarted = Infrastructure.startEvents(
        Map.of("farol.outbox.enabled", "true", "farol.outbox.poll-interval", "100ms"))) {
      await().atMost(Duration.ofSeconds(30)).until(() -> row(messageId).get("published_at") != null);
      // Several more runs (every 100 ms) before the queue is read: none may send it again.
      await().pollDelay(Duration.ofSeconds(1)).atMost(Duration.ofSeconds(3)).until(() -> true);
    }

    var messages = drain(QUEUE);
    assertThat(count(messages, messageId)).isEqualTo(1);
    assertThat(find(messages, messageId).getMessageProperties().getType()).isEqualTo("EventChanged");
  }

  @Test
  @DisplayName("192 AC-7 a publish by a broker user without write permission on farol.events is refused and "
      + "the row is kept")
  void refusedWithoutWritePermission() {
    var messageId = addEvent("event.changed");
    var user = "events-nowrite-" + UUID.randomUUID();
    Infrastructure.rabbitmqctl("add_user", user, "test-only-nowrite");
    var fresh = Infrastructure.publisherTemplate(user, "test-only-nowrite");
    try {
      Infrastructure.rabbitmqctl("set_permissions", user, "^$", "^farol\\.dead-letters$", "^events\\..*");

      assertThat(publisher(fresh).publishDue()).isZero();

      var failed = row(messageId);
      assertThat(failed.get("published_at")).isNull();
      assertThat(failed).containsEntry("attempts", 1);
      // The broker closes the channel on the refused publish; the client reports the pending confirm as a nack
      assertThat((String) failed.get("last_error")).startsWith("not confirmed: ");
      assertThat(drain(QUEUE)).isEmpty();
    } finally {
      ((CachingConnectionFactory) fresh.getConnectionFactory()).destroy();
      Infrastructure.rabbitmqctl("delete_user", user);
    }
  }

  @Test
  @DisplayName("192 AC-7 Events starts before its broker user exists and publishes once the user is created")
  void startsBeforeTheBrokerUser() throws Exception {
    var late = "events-late-" + UUID.randomUUID();
    var created = false;
    var messageId = addEvent("event.changed");
    var token = JWKS.valid(UUID.randomUUID().toString());
    try {
      try (var app = Infrastructure.startEvents(Map.of("spring.rabbitmq.username", late,
          "spring.rabbitmq.password", "test-only-late", "farol.outbox.enabled", "true",
          "farol.outbox.poll-interval", "100ms"));
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

        Infrastructure.rabbitmqctl("add_user", late, "test-only-late");
        created = true;
        Infrastructure.rabbitmqctl("set_permissions", late, "^$", "^(farol\\.events|farol\\.dead-letters)$",
            "^events\\..*");

        await().atMost(Duration.ofSeconds(30)).until(() -> row(messageId).get("published_at") != null);
      }
      assertThat(count(drain(QUEUE), messageId)).isEqualTo(1);
    } finally {
      if (created) {
        Infrastructure.rabbitmqctl("delete_user", late); // only after the context closed its connections
      }
    }
  }
}
