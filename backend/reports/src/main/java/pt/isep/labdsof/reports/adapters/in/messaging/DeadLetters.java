package pt.isep.labdsof.reports.adapters.in.messaging;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.stereotype.Component;

/**
 * Sends a message that cannot be processed to its queue's dead-letter queue, with the reason in the
 * {@value #ERROR_HEADER} header (ADR-004 decision 3, spec D8). Also the recoverer after the last retry.
 */
@Component
public class DeadLetters implements MessageRecoverer {

  public static final String EXCHANGE = "farol.dead-letters";
  public static final String ERROR_HEADER = "x-farol-error";

  private final RabbitTemplate rabbit;

  DeadLetters(RabbitTemplate rabbit) {
    this.rabbit = rabbit;
  }

  @Override
  public void recover(Message message, Throwable cause) {
    var root = NestedExceptionUtils.getMostSpecificCause(cause);
    var reason = root.getClass().getSimpleName() + ": " + root.getMessage();
    var properties = message.getMessageProperties();
    properties.setHeader(ERROR_HEADER, reason.substring(0, Math.min(500, reason.length())));
    rabbit.send(EXCHANGE, properties.getConsumerQueue() + ".dlq", message);
  }
}
