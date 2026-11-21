package pt.isep.labdsof.transports.adapters.in.messaging;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.stereotype.Component;

/**
 * Sends a message that cannot be processed to its queue's dead-letter queue, with the reason in the
 * {@value #ERROR_HEADER} header (ADR-004 decision 3, spec D3). Also the recoverer after the last retry.
 * The dead letter is persistent and confirmed before the original is acknowledged; if the broker does not
 * confirm it, the exception makes the container requeue the original, so it is never lost.
 */
@Component
public class DeadLetters implements MessageRecoverer {

  public static final String EXCHANGE = "farol.dead-letters";
  public static final String ERROR_HEADER = "x-farol-error";
  static final Duration CONFIRM_TIMEOUT = Duration.ofSeconds(5);

  private final RabbitTemplate rabbit;
  /** Not final so that the unit test can shorten the wait. */
  Duration confirmTimeout = CONFIRM_TIMEOUT;

  DeadLetters(RabbitTemplate rabbit) {
    this.rabbit = rabbit;
  }

  @Override
  public void recover(Message message, Throwable cause) {
    var properties = message.getMessageProperties();
    properties.setHeader(ERROR_HEADER, describe(cause));
    properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT); // cleared on a received message
    var correlation = new CorrelationData(UUID.randomUUID().toString());
    rabbit.send(EXCHANGE, properties.getConsumerQueue() + ".dlq", message, correlation);
    try {
      var confirm = correlation.getFuture().get(confirmTimeout.toMillis(), TimeUnit.MILLISECONDS);
      if (!confirm.ack() || correlation.getReturned() != null) {
        throw new AmqpException("dead letter not stored: " + confirm.reason());
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AmqpException(e);
    } catch (ExecutionException | TimeoutException e) {
      throw new AmqpException(e);
    }
  }

  /** The root cause as "Class: message", at most 500 characters (the x-farol-error header). */
  public static String describe(Throwable e) {
    var cause = NestedExceptionUtils.getMostSpecificCause(e);
    var text = cause.getClass().getSimpleName() + ": " + cause.getMessage();
    return text.substring(0, Math.min(500, text.length()));
  }
}
