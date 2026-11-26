package pt.isep.labdsof.authusers.adapters.in.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import pt.isep.labdsof.authusers.application.EventCopies;
import tools.jackson.databind.json.JsonMapper;

class EventCopyListenerTest {

  private static final String VALID = """
      {"messageId":"d29796b1-e0f8-43f0-8c0c-07afe7349c79","schemaVersion":1,
       "occurredAt":"2026-11-21T00:00:00Z","producer":"events","actorUserId":"",
       "type":"EventChanged","eventId":"8babb5c8-c462-4bd2-8a3f-da215d095d24",
       "payload":{"name":"Queima","status":"Published","version":1}}
      """;

  @ParameterizedTest
  @MethodSource("invalidEnvelopes")
  void rejectsBeforeProjectingAndClearsCorrelation(String invalid) {
    var copies = mock(EventCopies.class);
    var deadLetters = mock(DeadLetters.class);
    var listener = new EventCopyListener(copies, deadLetters, JsonMapper.builder().build());
    var properties = new MessageProperties();
    properties.setCorrelationId("test-correlation");
    var message = new Message(invalid.getBytes(StandardCharsets.UTF_8), properties);
    doAnswer(invocation -> {
      assertThat(MDC.get("correlationId")).isEqualTo("test-correlation");
      return null;
    }).when(deadLetters).recover(same(message), any(Throwable.class));

    listener.onEvent(message);

    verify(deadLetters).recover(same(message), any(Throwable.class));
    verifyNoInteractions(copies);
    assertThat(MDC.get("correlationId")).isNull();
  }

  @Test
  void acknowledgesUnknownTypesWithoutRequiringAKnownPayloadVersion() {
    var copies = mock(EventCopies.class);
    var deadLetters = mock(DeadLetters.class);
    var listener = new EventCopyListener(copies, deadLetters, JsonMapper.builder().build());
    String unknown = VALID.replace("EventChanged", "FutureEvent").replace(",\"version\":1", "");

    listener.onEvent(new Message(unknown.getBytes(StandardCharsets.UTF_8)));

    verifyNoInteractions(copies, deadLetters);
  }

  static Stream<String> invalidEnvelopes() {
    return Stream.of("[]", "{", VALID.replace("\"schemaVersion\":1", "\"schemaVersion\":2"),
        VALID.replace("\"schemaVersion\":1", "\"schemaVersion\":\"1\""),
        VALID.replace("\"schemaVersion\":1", "\"schemaVersion\":18446744073709551617"),
        VALID.replace("d29796b1-e0f8-43f0-8c0c-07afe7349c79", "invalid"),
        VALID.replace("8babb5c8-c462-4bd2-8a3f-da215d095d24", "invalid"),
        VALID.replace("2026-11-21T00:00:00Z", "invalid"),
        VALID.replace("\"producer\":\"events\"", "\"producer\":\"reports\""),
        VALID.replace(",\"actorUserId\":\"\"", ""),
        VALID.replace(",\"version\":1", ""),
        VALID.replace("\"version\":1", "\"version\":0"),
        VALID.replace("\"version\":1", "\"version\":\"1\""),
        VALID.replace("\"version\":1", "\"version\":18446744073709551617"),
        VALID.replace("\"Queima\"", "\" \""));
  }
}
