package pt.isep.labdsof.events.adapters.in.messaging;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.function.Consumer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.connection.CorrelationData.Confirm;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

class DeadLettersTest {

  private enum Failure { NACK, RETURNED, EXCEPTIONAL_FUTURE, TIMEOUT }

  private final RabbitTemplate rabbit = mock(RabbitTemplate.class);
  private final DeadLetters deadLetters = new DeadLetters(rabbit);

  private static Message message() {
    var properties = new MessageProperties();
    properties.setConsumerQueue("events.auth-users-events");
    return new Message("not json".getBytes(UTF_8), properties);
  }

  /** The broker's answer to the send: what the confirm callback does with the correlation data. */
  private void broker(Consumer<CorrelationData> answer) {
    doAnswer(invocation -> {
      answer.accept(invocation.getArgument(3));
      return null;
    }).when(rabbit).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
  }

  @Test
  @DisplayName("192 AC-6 a dead letter goes to farol.dead-letters as the queue's dlq, persistent, with "
      + "x-farol-error")
  void deadLetterShape() {
    broker(correlation -> correlation.getFuture().complete(new Confirm(true, null)));
    var message = message();

    deadLetters.recover(message, new IllegalArgumentException("eventId is required"));

    var sent = ArgumentCaptor.forClass(Message.class);
    verify(rabbit).send(eq("farol.dead-letters"), eq("events.auth-users-events.dlq"), sent.capture(),
        any(CorrelationData.class));
    assertThat(new String(sent.getValue().getBody(), UTF_8)).isEqualTo("not json");
    assertThat(sent.getValue().getMessageProperties().getDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
    assertThat(sent.getValue().getMessageProperties().<String>getHeader("x-farol-error"))
        .isEqualTo("IllegalArgumentException: eventId is required");
  }

  @ParameterizedTest(name = "{0}")
  @EnumSource(Failure.class)
  @DisplayName("192 AC-6 a dead letter the broker nacks, returns, fails or does not confirm in time throws, so "
      + "the original is requeued")
  void aDeadLetterNotStoredThrows(Failure failure) {
    deadLetters.confirmTimeout = Duration.ofMillis(100);
    switch (failure) {
      case NACK -> broker(c -> c.getFuture().complete(new Confirm(false, "no space")));
      case RETURNED -> broker(c -> {
        c.setReturned(new ReturnedMessage(message(), 312, "NO_ROUTE", "farol.dead-letters", "x"));
        c.getFuture().complete(new Confirm(true, null));
      });
      case EXCEPTIONAL_FUTURE -> broker(c -> c.getFuture().completeExceptionally(new IllegalStateException("x")));
      case TIMEOUT -> broker(c -> { });
    }

    assertThatThrownBy(() -> deadLetters.recover(message(), new IllegalStateException("db down")))
        .isInstanceOf(AmqpException.class);
  }

  @Test
  @DisplayName("192 AC-6 an interrupted wait for the confirm throws and keeps the interrupt flag")
  void interruptedWaitKeepsTheFlag() {
    broker(c -> { });
    Thread.currentThread().interrupt();
    try {
      assertThatThrownBy(() -> deadLetters.recover(message(), new IllegalStateException("db down")))
          .isInstanceOf(AmqpException.class).hasCauseInstanceOf(InterruptedException.class);
      assertThat(Thread.currentThread().isInterrupted()).isTrue();
    } finally {
      Thread.interrupted(); // clear the flag for the next test
    }
  }

  @Test
  @DisplayName("192 AC-6 the reason is the root cause as Class: message, at most 500 characters")
  void reasonIsTheRootCause() {
    var nested = new RuntimeException("outer", new IllegalStateException("inner"));
    var prefix = "IllegalStateException: ";

    assertThat(DeadLetters.describe(nested)).isEqualTo("IllegalStateException: inner");
    assertThat(DeadLetters.describe(new IllegalStateException("x".repeat(500 - prefix.length() - 1))))
        .hasSize(499);
    assertThat(DeadLetters.describe(new IllegalStateException("x".repeat(500 - prefix.length()))))
        .hasSize(500).endsWith("x");
    assertThat(DeadLetters.describe(new IllegalStateException("x".repeat(500 - prefix.length() + 1))))
        .hasSize(500).endsWith("x");
    assertThat(DeadLetters.describe(new IllegalStateException("x".repeat(2000)))).hasSize(500).startsWith(prefix);
  }
}
