package pt.isep.labdsof.events.adapters.out.messaging;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import java.net.ConnectException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.stubbing.Answer;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.support.TransactionOperations;
import pt.isep.labdsof.events.Correlation;
import pt.isep.labdsof.events.adapters.out.persistence.JdbcOutbox;
import pt.isep.labdsof.events.adapters.out.persistence.JdbcOutbox.Pending;

/** The outbox publisher's decisions, with the database and the broker mocked. */
class OutboxPublisherTest {

  static final Instant T = Instant.parse("2026-11-17T10:00:00Z");
  static final int BATCH = 7;
  static final String BODY = "{\"payload\":{\"organiserName\":\"Ana Secret\",\"email\":\"ana@x\"}}";

  final JdbcOutbox outbox = mock(JdbcOutbox.class);
  final RabbitTemplate rabbit = mock(RabbitTemplate.class);
  final MutableClock clock = new MutableClock(T);
  final OutboxPublisher publisher = new OutboxPublisher(outbox, rabbit,
      TransactionOperations.withoutTransaction(), clock, BATCH, Duration.ofMillis(50));
  final List<LogLine> lines = new ArrayList<>();
  final CapturingAppender appender = new CapturingAppender(lines);
  final ch.qos.logback.classic.Logger publisherLog =
      (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(OutboxPublisher.class);
  Level previousLevel;

  @BeforeEach
  void attach() {
    MDC.clear();
    previousLevel = publisherLog.getLevel();
    publisherLog.setLevel(Level.INFO);
    appender.start();
    publisherLog.addAppender(appender);
  }

  @AfterEach
  void detach() {
    publisherLog.detachAppender(appender);
    appender.stop();
    publisherLog.setLevel(previousLevel);
    MDC.clear();
    Thread.interrupted(); // a test that leaves the flag set must not leak it
  }

  static Pending row(long id, String correlationId, int attempts) {
    return new Pending(id, new UUID(0, id), "farol.events", "event.changed", "EventChanged",
        BODY, correlationId, attempts);
  }

  static Pending row(long id) {
    return row(id, null, 0);
  }

  void send(Answer<?>... answers) {
    var stubbing = doAnswer(answers[0]);
    for (int i = 1; i < answers.length; i++) {
      stubbing = stubbing.doAnswer(answers[i]);
    }
    stubbing.when(rabbit).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
  }

  static Answer<Object> ack() {
    return invocation -> {
      correlation(invocation).getFuture().complete(new CorrelationData.Confirm(true, null));
      return null;
    };
  }

  static Answer<Object> returned() {
    return invocation -> {
      var correlation = correlation(invocation);
      correlation.setReturned(new ReturnedMessage(invocation.getArgument(2), 312, "NO_ROUTE",
          invocation.getArgument(0), invocation.getArgument(1)));
      correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
      return null;
    };
  }

  static Answer<Object> nack() {
    return invocation -> {
      correlation(invocation).getFuture().complete(new CorrelationData.Confirm(false, "nacked by test"));
      return null;
    };
  }

  static Answer<Object> silence() {
    return invocation -> null;
  }

  static Answer<Object> brokerDown() {
    return invocation -> {
      throw new AmqpConnectException(new ConnectException("Connection refused"));
    };
  }

  static Answer<Object> interrupt() {
    return invocation -> {
      Thread.currentThread().interrupt();
      return null;
    };
  }

  static CorrelationData correlation(org.mockito.invocation.InvocationOnMock invocation) {
    return invocation.getArgument(3);
  }

  @Test
  @DisplayName("192 AC-7 a due row is sent to its exchange and routing key as persistent JSON with messageId, "
      + "type and appId events, then marked published")
  void sendsADueRow() {
    when(outbox.claimDue(BATCH)).thenReturn(List.of(row(5)));
    send(ack());

    assertThat(publisher.publishDue()).isEqualTo(1);

    var message = ArgumentCaptor.forClass(Message.class);
    var correlation = ArgumentCaptor.forClass(CorrelationData.class);
    verify(rabbit).send(eq("farol.events"), eq("event.changed"), message.capture(),
        correlation.capture());
    var properties = message.getValue().getMessageProperties();
    assertThat(properties.getMessageId()).isEqualTo(new UUID(0, 5).toString());
    assertThat(properties.getType()).isEqualTo("EventChanged");
    assertThat(properties.getAppId()).isEqualTo("events");
    assertThat(properties.getContentType()).isEqualTo(MessageProperties.CONTENT_TYPE_JSON);
    assertThat(properties.getContentEncoding()).isEqualTo("UTF-8");
    assertThat(properties.getDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
    assertThat(message.getValue().getBody()).isEqualTo(BODY.getBytes(UTF_8));
    assertThat(correlation.getValue().getId()).isEqualTo(new UUID(0, 5).toString());
    verify(outbox).markPublished(5L, T);
    verify(outbox, never()).markFailed(anyLong(), anyInt(), any(), anyString());
  }

  @Test
  @DisplayName("192 AC-8 the row's correlation id is sent as the AMQP correlationId, and none is sent when "
      + "the row has none")
  void sendsTheCorrelationId() {
    when(outbox.claimDue(BATCH)).thenReturn(List.of(row(1, "c-1", 0), row(2)));
    send(ack(), ack());

    assertThat(publisher.publishDue()).isEqualTo(2);

    var messages = ArgumentCaptor.forClass(Message.class);
    verify(rabbit, times(2)).send(anyString(), anyString(), messages.capture(), any(CorrelationData.class));
    assertThat(messages.getAllValues().get(0).getMessageProperties().getCorrelationId()).isEqualTo("c-1");
    assertThat(messages.getAllValues().get(1).getMessageProperties().getCorrelationId()).isNull();
  }

  @Test
  @DisplayName("192 AC-7 an unroutable row backs off alone and the batch goes on")
  void unroutableBacksOffAlone() {
    when(outbox.claimDue(BATCH)).thenReturn(List.of(row(1), row(2))).thenReturn(List.of());
    send(returned(), ack());

    assertThat(publisher.publishDue()).isEqualTo(1);

    verify(outbox).markFailed(eq(1L), eq(1), eq(T.plusSeconds(2)), contains("unroutable"));
    verify(outbox).markPublished(2L, T);
    verify(rabbit, times(2)).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
    // not paused: the next run claims again at the same instant
    assertThat(publisher.publishDue()).isZero();
    verify(outbox, times(2)).claimDue(BATCH);
  }

  @Test
  @DisplayName("192 AC-7 a nacked row backs off alone and the batch goes on")
  void nackBacksOffAlone() {
    when(outbox.claimDue(BATCH)).thenReturn(List.of(row(1), row(2))).thenReturn(List.of());
    send(nack(), ack());

    assertThat(publisher.publishDue()).isEqualTo(1);

    verify(outbox).markFailed(1L, 1, T.plusSeconds(2), "not confirmed: nacked by test");
    verify(outbox).markPublished(2L, T);
    assertThat(publisher.publishDue()).isZero();
    verify(outbox, times(2)).claimDue(BATCH);
  }

  @Test
  @DisplayName("192 AC-7 when the broker is down the row backs off, the batch stops and the publisher "
      + "pauses until the row is due")
  void brokerDownPauses() {
    when(outbox.claimDue(BATCH)).thenReturn(List.of(row(1), row(2)));
    send(brokerDown());

    assertThat(publisher.publishDue()).isZero();

    verify(outbox).markFailed(1L, 1, T.plusSeconds(2), "ConnectException: Connection refused");
    verify(rabbit, times(1)).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
    verify(outbox, never()).markPublished(anyLong(), any());

    assertThat(publisher.publishDue()).isZero(); // paused: no database access at all
    clock.set(T.plusSeconds(2).minusMillis(1));
    assertThat(publisher.publishDue()).isZero();
    verify(outbox, times(1)).claimDue(BATCH);

    clock.set(T.plusSeconds(2)); // exactly due: claims again
    when(outbox.claimDue(BATCH)).thenReturn(List.of());
    assertThat(publisher.publishDue()).isZero();
    verify(outbox, times(2)).claimDue(BATCH);
  }

  @Test
  @DisplayName("192 AC-7 a confirm that does not arrive in time is handled as a broker that is down")
  void confirmTimeoutPauses() {
    when(outbox.claimDue(BATCH)).thenReturn(List.of(row(1), row(2)));
    send(silence());

    assertThat(publisher.publishDue()).isZero();

    verify(outbox).markFailed(1L, 1, T.plusSeconds(2), "TimeoutException");
    verify(rabbit, times(1)).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
    assertThat(publisher.publishDue()).isZero();
    verify(outbox, times(1)).claimDue(BATCH);
  }

  @Test
  @DisplayName("192 AC-7 an interrupt while waiting for a confirm is not counted as an attempt and is kept")
  void interruptIsNotAnAttempt() {
    when(outbox.claimDue(BATCH)).thenReturn(List.of(row(1), row(2)));
    send(interrupt());

    assertThat(publisher.publishDue()).isZero();

    assertThat(Thread.currentThread().isInterrupted()).isTrue();
    verify(outbox, never()).markFailed(anyLong(), anyInt(), any(), anyString());
    verify(outbox, never()).markPublished(anyLong(), any());
    verify(rabbit, times(1)).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));

    assertThat(Thread.interrupted()).isTrue(); // clears the flag; the publisher was not paused
    assertThat(publisher.publishDue()).isZero();
    verify(outbox, times(2)).claimDue(BATCH);
  }

  @Test
  @DisplayName("192 AC-7 a failed row's attempts grow from the stored count and its backoff stops at 60 s")
  void attemptsGrowFromTheStoredCount() {
    when(outbox.claimDue(BATCH)).thenReturn(List.of(row(1, null, 5)));
    send(brokerDown());

    publisher.publishDue();

    verify(outbox).markFailed(eq(1L), eq(6), eq(T.plusSeconds(60)), anyString());
  }

  @Test
  @DisplayName("192 AC-7 a run claims at most batch-size rows and sends nothing when nothing is due")
  void claimsTheBatchSize() {
    when(outbox.claimDue(BATCH)).thenReturn(List.of());

    assertThat(publisher.publishDue()).isZero();

    verify(outbox).claimDue(BATCH);
    verifyNoInteractions(rabbit);
    verify(outbox, never()).markPublished(anyLong(), any());
  }

  @Test
  @DisplayName("192 AC-7 a database failure while marking a row is not taken for a broker failure")
  void databaseFailureIsNotABrokerFailure() {
    when(outbox.claimDue(BATCH)).thenReturn(List.of(row(1)));
    send(ack());
    doThrow(new DataAccessResourceFailureException("db down")).when(outbox).markPublished(anyLong(), any());

    assertThatThrownBy(publisher::publishDue).isInstanceOf(DataAccessResourceFailureException.class);

    verify(outbox, never()).markFailed(anyLong(), anyInt(), any(), anyString());
    // not paused: the next run claims at the same instant
    org.mockito.Mockito.doNothing().when(outbox).markPublished(anyLong(), any());
    send(ack());
    assertThat(publisher.publishDue()).isEqualTo(1);
    verify(outbox, times(2)).claimDue(BATCH);
  }

  @Test
  @DisplayName("192 AC-8 the publisher logs each row with its correlation id and without the message body")
  void logsWithTheCorrelationIdWithoutTheBody() {
    when(outbox.claimDue(BATCH)).thenReturn(List.of(row(1, "c-1", 0), row(2, "c-2", 0), row(3)));
    send(ack(), nack(), ack());

    assertThat(publisher.publishDue()).isEqualTo(2);

    assertThat(lines).hasSize(3);
    assertThat(lines.get(0).level()).isEqualTo(Level.INFO);
    assertThat(lines.get(0).mdc()).containsEntry(Correlation.MDC_KEY, "c-1");
    assertThat(lines.get(0).message()).contains(new UUID(0, 1).toString(), "EventChanged");
    assertThat(lines.get(1).level()).isEqualTo(Level.WARN);
    assertThat(lines.get(1).mdc()).containsEntry(Correlation.MDC_KEY, "c-2");
    assertThat(lines.get(1).message()).contains("attempt 1", "nacked by test");
    assertThat(lines.get(2).level()).isEqualTo(Level.INFO);
    assertThat(lines.get(2).mdc()).doesNotContainKey(Correlation.MDC_KEY);
    assertThat(lines).allSatisfy(line -> assertThat(line.message()).doesNotContain("Ana Secret", "ana@x"));
    assertThat(MDC.get(Correlation.MDC_KEY)).isNull();
  }

  @Test
  @DisplayName("192 AC-7 the stored failure reason is the root cause with its class, or the class alone "
      + "without a message")
  void describesTheRootCause() {
    assertThat(OutboxPublisher.describe(new IllegalStateException("outer",
        new AmqpConnectException(new ConnectException("Connection refused")))))
        .isEqualTo("ConnectException: Connection refused");
    assertThat(OutboxPublisher.describe(new IllegalStateException("outer", new TimeoutException())))
        .isEqualTo("TimeoutException");
    assertThat(OutboxPublisher.describe(new OutboxPublisher.Refused("not confirmed: x")))
        .isEqualTo("not confirmed: x");
  }

  /** What a log call looked like when it was made: the MDC is read by Logback lazily, so it is copied now. */
  record LogLine(Level level, String message, Map<String, String> mdc) {}

  static final class CapturingAppender extends AppenderBase<ILoggingEvent> {

    private final List<LogLine> lines;

    CapturingAppender(List<LogLine> lines) {
      this.lines = lines;
    }

    @Override
    protected void append(ILoggingEvent event) {
      lines.add(new LogLine(event.getLevel(), event.getFormattedMessage(),
          Map.copyOf(event.getMDCPropertyMap())));
    }
  }

  /** A clock the test moves. */
  static final class MutableClock extends Clock {

    private Instant now;

    MutableClock(Instant now) {
      this.now = now;
    }

    void set(Instant instant) {
      this.now = instant;
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }
}
