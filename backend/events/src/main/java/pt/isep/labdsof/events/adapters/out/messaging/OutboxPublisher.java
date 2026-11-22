package pt.isep.labdsof.events.adapters.out.messaging;

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
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;
import pt.isep.labdsof.events.Correlation;
import pt.isep.labdsof.events.adapters.out.persistence.JdbcOutbox;
import pt.isep.labdsof.events.adapters.out.persistence.JdbcOutbox.Pending;
import pt.isep.labdsof.events.application.OutboxBackoff;
import pt.isep.labdsof.events.application.OutgoingEvent;

/**
 * Publishes the outbox to RabbitMQ (ADR-004 decision 3, spec D1, AC-7): due rows are claimed with
 * FOR UPDATE SKIP LOCKED, sent with publisher confirms and mandatory routing, and marked in the same
 * transaction. A row the broker refuses (unroutable, nack) backs off alone (2^attempts s, at most 60 s);
 * any other failure (the broker is down) backs off the row, ends the batch and pauses the publisher until
 * that row is due again, so rows go out in id order and a down broker is not tried at every poll.
 * The row's correlation id is sent as the AMQP correlationId (AC-8), and is in the MDC of the log lines
 * of that row.
 */
@Component
@ConditionalOnProperty(name = "farol.outbox.enabled", havingValue = "true")
public class OutboxPublisher {

  private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

  private final JdbcOutbox outbox;
  private final RabbitTemplate rabbit;
  private final TransactionOperations tx;
  private final Clock clock;
  private final int batchSize;
  private final Duration confirmTimeout;
  private Instant pausedUntil = Instant.MIN;

  OutboxPublisher(JdbcOutbox outbox, RabbitTemplate rabbit, TransactionOperations tx, Clock clock,
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
      MDC.put(Correlation.MDC_KEY, row.correlationId());
    }
    try {
      try {
        send(row);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt(); // shutting down: not a failed attempt
        return false;
      } catch (ExecutionException | TimeoutException | RuntimeException e) {
        return failed(row, e);
      }
      // A database error here propagates and rolls the batch back: rows already sent are sent again later
      // (at least once; consumers dedupe by messageId, #214 D6).
      outbox.markPublished(row.id(), clock.instant());
      log.info("Outbox message {} ({}) published", row.messageId(), row.type());
      sent.incrementAndGet();
      return true;
    } finally {
      MDC.remove(Correlation.MDC_KEY);
    }
  }

  private boolean failed(Pending row, Exception e) {
    int attempts = row.attempts() + 1;
    var error = describe(e);
    var next = clock.instant().plus(OutboxBackoff.after(attempts));
    outbox.markFailed(row.id(), attempts, next, error);
    log.warn("Outbox message {} ({}) not published, attempt {}, next attempt at {}: {}", row.messageId(),
        row.type(), attempts, next, error);
    if (e instanceof Refused) {
      return true;
    }
    pausedUntil = next; // the broker is most likely down: the rest waits, in order
    return false;
  }

  private void send(Pending row) throws InterruptedException, ExecutionException, TimeoutException {
    var properties = new MessageProperties();
    properties.setMessageId(row.messageId().toString());
    properties.setType(row.type());
    properties.setAppId(OutgoingEvent.PRODUCER);
    properties.setCorrelationId(row.correlationId()); // null sends no property
    properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
    properties.setContentEncoding(UTF_8.name());
    properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
    var correlation = new CorrelationData(row.messageId().toString());
    rabbit.send(row.exchange(), row.routingKey(), new Message(row.body().getBytes(UTF_8), properties),
        correlation);
    var confirm = correlation.getFuture().get(confirmTimeout.toMillis(), TimeUnit.MILLISECONDS);
    var returned = correlation.getReturned();
    if (returned != null) {
      throw new Refused("unroutable: " + returned.getReplyCode() + " " + returned.getReplyText());
    }
    if (!confirm.ack()) {
      throw new Refused("not confirmed: " + confirm.reason());
    }
  }

  /**
   * The most specific cause as {@code Simple: message}, or the class alone when it has no message; the
   * reason of a refusal is already a sentence of its own.
   */
  static String describe(Throwable e) {
    if (e instanceof Refused) {
      return e.getMessage();
    }
    var cause = NestedExceptionUtils.getMostSpecificCause(e);
    var name = cause.getClass().getSimpleName();
    return cause.getMessage() == null ? name : name + ": " + cause.getMessage();
  }

  /** The broker is up but refused this one row. */
  static class Refused extends AmqpException {
    Refused(String reason) {
      super(reason);
    }
  }
}
