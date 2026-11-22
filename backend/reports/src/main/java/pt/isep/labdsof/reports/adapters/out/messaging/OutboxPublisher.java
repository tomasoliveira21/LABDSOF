package pt.isep.labdsof.reports.adapters.out.messaging;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import pt.isep.labdsof.reports.adapters.in.messaging.DeadLetters;
import pt.isep.labdsof.reports.adapters.out.persistence.JdbcOutbox;
import pt.isep.labdsof.reports.adapters.out.persistence.JdbcOutbox.Pending;

/**
 * Publishes the outbox to RabbitMQ (ADR-004 decision 3, spec D5): due rows are claimed with
 * FOR UPDATE SKIP LOCKED, sent with publisher confirms and mandatory routing, and marked in the same
 * transaction. A row the broker refuses (unroutable, nack) backs off alone (2^attempts s, at most 60 s);
 * any other failure (the broker is down) backs off the row, ends the batch and pauses the publisher until
 * that row is due again, so rows go out in id order and a down broker is not tried at every poll.
 */
@Component
@ConditionalOnProperty(name = "farol.outbox.enabled", havingValue = "true")
public class OutboxPublisher {

  private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
  static final Duration MAX_BACKOFF = Duration.ofSeconds(60);

  private final JdbcOutbox outbox;
  private final RabbitTemplate rabbit;
  private final TransactionTemplate tx;
  private final Clock clock;
  private final int batchSize;
  private final Duration confirmTimeout;
  private Instant pausedUntil = Instant.MIN;

  OutboxPublisher(JdbcOutbox outbox, RabbitTemplate rabbit, TransactionTemplate tx, Clock clock,
      @Value("${farol.outbox.batch-size}") int batchSize,
      @Value("${farol.outbox.confirm-timeout}") Duration confirmTimeout) {
    this.outbox = outbox;
    this.rabbit = rabbit;
    this.tx = tx;
    this.clock = clock;
    this.batchSize = batchSize;
    this.confirmTimeout = confirmTimeout;
  }

  /** One run; returns how many rows were published. */
  @Scheduled(fixedDelayString = "${farol.outbox.poll-interval}")
  public int publishDue() {
    if (clock.instant().isBefore(pausedUntil)) {
      return 0;
    }
    var sent = new AtomicInteger();
    tx.executeWithoutResult(status -> {
      for (Pending row : outbox.claimDue(batchSize)) {
        if (!publish(row, sent)) {
          break;
        }
      }
    });
    return sent.get();
  }

  /** Sends one row; false when the batch must stop (shutting down, or the broker is most likely down). */
  private boolean publish(Pending row, AtomicInteger sent) {
    if (row.correlationId() != null) {
      MDC.put("correlationId", row.correlationId()); // #148: the request that wrote the row
    }
    try {
      send(row);
      outbox.markPublished(row.id(), clock.instant());
      sent.incrementAndGet();
      log.info("Outbox message {} ({}) published", row.messageId(), row.type());
      return true;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt(); // shutting down: not a failed attempt
      return false;
    } catch (Exception e) {
      int attempts = row.attempts() + 1;
      var error = DeadLetters.describe(e);
      var backoff = backoff(attempts);
      outbox.markFailed(row.id(), attempts, backoff, error);
      log.warn("Outbox message {} ({}) not published, attempt {}: {}", row.messageId(), row.type(),
          attempts, error);
      if (e instanceof Refused) {
        return true;
      }
      pausedUntil = clock.instant().plus(backoff); // the broker is most likely down: the rest waits, in order
      return false;
    } finally {
      MDC.remove("correlationId");
    }
  }

  static Duration backoff(int attempts) {
    return attempts >= 6 ? MAX_BACKOFF : Duration.ofSeconds(1L << attempts);
  }

  private void send(Pending row) throws InterruptedException, ExecutionException, TimeoutException {
    var properties = new MessageProperties();
    properties.setMessageId(row.messageId().toString());
    properties.setType(row.type());
    properties.setAppId("reports");
    properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
    properties.setContentEncoding(UTF_8.name());
    properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
    properties.setCorrelationId(row.correlationId()); // #148: AMQP correlation_id, null when the row has none
    var correlation = new CorrelationData(row.messageId().toString());
    rabbit.send(row.exchange(), row.routingKey(), new Message(row.body().getBytes(UTF_8), properties),
        correlation);
    var confirm = correlation.getFuture().get(confirmTimeout.toMillis(), TimeUnit.MILLISECONDS);
    if (correlation.getReturned() != null) {
      throw new Refused("unroutable: " + correlation.getReturned().getReplyText());
    }
    if (!confirm.ack()) {
      throw new Refused("not confirmed: " + confirm.reason());
    }
  }

  /** The broker is up but refused this one row. */
  static class Refused extends IllegalStateException {
    Refused(String reason) {
      super(reason);
    }
  }
}
