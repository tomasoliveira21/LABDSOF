package pt.isep.labdsof.reports.adapters.out.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;
import pt.isep.labdsof.reports.Infrastructure;
import pt.isep.labdsof.reports.IntegrationTest;
import pt.isep.labdsof.reports.adapters.out.persistence.JdbcOutbox;
import pt.isep.labdsof.reports.application.HelpRequestService;
import pt.isep.labdsof.reports.application.OutgoingEvent;
import pt.isep.labdsof.reports.domain.Location;
import pt.isep.labdsof.reports.domain.Membership;
import tools.jackson.databind.json.JsonMapper;

/** AC-2: the outbox with real PostgreSQL and RabbitMQ (ADR-004, spec D4 to D6). */
class OutboxIT extends IntegrationTest {

  static final String QUEUE = "reports.reports-events";
  static final Location POINT = new Location(3, 0.42, 0.77);

  @Autowired HelpRequestService service;
  @Autowired JdbcOutbox outbox;
  @Autowired RabbitTemplate rabbit;
  @Autowired TransactionTemplate tx;
  @Autowired JsonMapper json;

  final RabbitTemplate admin = Infrastructure.adminTemplate();
  final UUID event = UUID.randomUUID();

  @BeforeEach
  void cleanSlate() {
    // Rows left by other test classes are not this test's concern; the broker queue starts empty too.
    jdbc.sql("UPDATE outbox SET published_at = now() WHERE published_at IS NULL").update();
    drain();
    member(event, "ana", Membership.Role.RegularUser);
  }

  OutboxPublisher publisher(RabbitTemplate template) {
    return new OutboxPublisher(outbox, template, tx, Clock.systemUTC(), 50, Duration.ofSeconds(2));
  }

  UUID submit() {
    var request = service.submit(event, "ana", UUID.randomUUID(), "help", POINT).request();
    return messageIdOf(request.id());
  }

  UUID messageIdOf(UUID requestId) {
    return jdbc.sql("SELECT message_id FROM outbox WHERE body->'payload'->>'requestId' = :id")
        .param("id", requestId.toString()).query(UUID.class).single();
  }

  Map<String, Object> row(UUID messageId) {
    return jdbc.sql("SELECT attempts, next_attempt_at, published_at, last_error FROM outbox "
        + "WHERE message_id = :id").param("id", messageId).query().singleRow();
  }

  /** Every message waiting in the queue, by messageId. */
  List<Message> drain() {
    var messages = new ArrayList<Message>();
    Message message;
    while ((message = admin.receive(QUEUE, 500)) != null) {
      messages.add(message);
    }
    return messages;
  }

  long count(List<Message> messages, UUID messageId) {
    return messages.stream()
        .filter(m -> messageId.toString().equals(m.getMessageProperties().getMessageId())).count();
  }

  @Test
  @DisplayName("194 AC-2 the request and its outbox row commit or roll back together")
  void sameTransaction() {
    var client = UUID.randomUUID();
    try {
      tx.executeWithoutResult(status -> {
        service.submit(event, "ana", client, "help", POINT);
        throw new IllegalStateException("crash before commit");
      });
    } catch (IllegalStateException expected) {
      // rolled back
    }
    assertThat(jdbc.sql("SELECT count(*) FROM help_request WHERE client_request_id = :c").param("c", client)
        .query(Long.class).single()).isZero();
    assertThat(jdbc.sql("SELECT count(*) FROM outbox WHERE body->>'eventId' = :e")
        .param("e", event.toString()).query(Long.class).single()).isZero();
  }

  @Test
  @DisplayName("194 AC-2 a crash between save and publish delivers HelpRequestSubmitted exactly once")
  void crashBetweenSaveAndPublish() throws Exception {
    // This context has no publisher: the request is committed and the service stops before any publish.
    var messageId = submit();
    assertThat(row(messageId).get("published_at")).isNull();

    // The restarted service, now with its publisher running.
    try (var restarted = Infrastructure.startReports(
        Map.of("farol.outbox.enabled", "true", "farol.outbox.poll-interval", "100ms"))) {
      await().atMost(Duration.ofSeconds(20)).until(() -> row(messageId).get("published_at") != null);
      Thread.sleep(1000); // several more publisher runs: none may send it again
    }
    var messages = drain();
    assertThat(count(messages, messageId)).isEqualTo(1);

    var message = messages.stream()
        .filter(m -> messageId.toString().equals(m.getMessageProperties().getMessageId())).findFirst()
        .orElseThrow();
    assertThat(message.getMessageProperties().getType()).isEqualTo("HelpRequestSubmitted");
    assertThat(message.getMessageProperties().getReceivedRoutingKey()).isEqualTo("help-request.submitted");
    var body = json.readTree(message.getBody());
    assertThat(body.get("messageId").asString()).isEqualTo(messageId.toString());
    assertThat(body.get("producer").asString()).isEqualTo("reports");
    assertThat(body.get("eventId").asString()).isEqualTo(event.toString());
    assertThat(body.get("actorUserId").asString()).isEqualTo("ana");
    assertThat(body.get("payload").propertyNames()).containsExactly("requestId");
  }

  @Test
  @DisplayName("194 AC-2 a retried submit adds no second event")
  void retriedSubmit() {
    var client = UUID.randomUUID();
    var first = service.submit(event, "ana", client, "help", POINT).request();
    service.submit(event, "ana", client, "help", POINT);
    var messageId = messageIdOf(first.id());

    assertThat(publisher(rabbit).publishDue()).isEqualTo(1);
    assertThat(count(drain(), messageId)).isEqualTo(1);
  }

  @Test
  @DisplayName("194 AC-2 the outbox keeps and retries events while RabbitMQ is down")
  void brokerDown() throws Exception {
    var messageId = submit();
    var unreachable = new CachingConnectionFactory("127.0.0.1", 1); // nothing listens on port 1
    try {
      var before = Instant.now();
      assertThat(publisher(new RabbitTemplate(unreachable)).publishDue()).isZero();
      var failed = row(messageId);
      assertThat(failed.get("published_at")).isNull();
      assertThat(failed.get("attempts")).isEqualTo(1);
      assertThat((String) failed.get("last_error")).isNotBlank();
      assertThat(((java.sql.Timestamp) failed.get("next_attempt_at")).toInstant())
          .isAfter(before.plusSeconds(1));

      // Not due yet: the next run leaves it alone
      assertThat(publisher(rabbit).publishDue()).isZero();

      // Due again, broker back: published once
      jdbc.sql("UPDATE outbox SET next_attempt_at = now() WHERE message_id = :id").param("id", messageId)
          .update();
      assertThat(publisher(rabbit).publishDue()).isEqualTo(1);
      assertThat(row(messageId).get("last_error")).isNull();
      assertThat(count(drain(), messageId)).isEqualTo(1);
    } finally {
      unreachable.destroy();
    }
  }

  @Test
  @DisplayName("194 AC-2 an unroutable event stays in the outbox")
  void unroutable() {
    var messageId = UUID.randomUUID();
    tx.executeWithoutResult(status -> outbox.add(new OutgoingEvent(messageId, "farol.reports",
        "nothing.bound", "Nothing", 1, Instant.now(), event, "", Map.of())));
    try {
      assertThat(publisher(rabbit).publishDue()).isZero();
      assertThat(row(messageId).get("published_at")).isNull();
      assertThat((String) row(messageId).get("last_error")).contains("unroutable");
    } finally {
      jdbc.sql("DELETE FROM outbox WHERE message_id = :id").param("id", messageId).update();
    }
  }

  @Test
  @DisplayName("194 AC-2 while RabbitMQ is down the publisher pauses and keeps the order")
  void pausesWhileBrokerDown() {
    var first = submit();
    var second = submit();
    var unreachable = new CachingConnectionFactory("127.0.0.1", 1);
    try {
      var down = publisher(new RabbitTemplate(unreachable));
      assertThat(down.publishDue()).isZero();
      assertThat(down.publishDue()).isZero(); // paused: the second row is not tried
      assertThat(row(first).get("attempts")).isEqualTo(1);
      assertThat(row(second).get("attempts")).isEqualTo(0);
    } finally {
      unreachable.destroy();
    }
  }

  @Test
  @DisplayName("194 AC-2 an unroutable event does not hold back the next one")
  void unroutableDoesNotBlock() {
    var refused = UUID.randomUUID();
    tx.executeWithoutResult(status -> outbox.add(new OutgoingEvent(refused, "farol.reports",
        "nothing.bound", "Nothing", 1, Instant.now(), event, "", Map.of())));
    try {
      var next = submit();
      assertThat(publisher(rabbit).publishDue()).isEqualTo(1);
      assertThat(row(refused).get("published_at")).isNull();
      assertThat(count(drain(), next)).isEqualTo(1);
    } finally {
      jdbc.sql("DELETE FROM outbox WHERE message_id = :id").param("id", refused).update();
    }
  }

  @Test
  @DisplayName("194 AC-2 two publishers never claim the same row")
  void skipLocked() throws Exception {
    var first = submit();
    var second = submit();
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
  @DisplayName("194 AC-2 the backoff doubles and is capped at 60 s")
  void backoff() {
    assertThat(OutboxPublisher.backoff(1)).isEqualTo(Duration.ofSeconds(2));
    assertThat(OutboxPublisher.backoff(5)).isEqualTo(Duration.ofSeconds(32));
    assertThat(OutboxPublisher.backoff(6)).isEqualTo(Duration.ofSeconds(60));
    assertThat(OutboxPublisher.backoff(40)).isEqualTo(Duration.ofSeconds(60));
    assertThat(Set.of(OutboxPublisher.MAX_BACKOFF)).containsExactly(Duration.ofSeconds(60));
  }
}
