package pt.isep.labdsof.events.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OutgoingEventTest {

  private static final UUID MESSAGE = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
  private static final UUID EVENT = UUID.fromString("11111111-2222-3333-4444-555555555555");
  private static final Instant AT = Instant.parse("2026-11-17T10:00:00.123456Z");
  private static final Map<String, Object> PAYLOAD =
      Map.of("name", "Queima das Fitas 2027", "status", "Published", "version", 1L);

  private static OutgoingEvent event(Map<String, Object> payload) {
    return new OutgoingEvent(MESSAGE, OutgoingEvent.EXCHANGE, "event.changed", "EventChanged", 1, AT, EVENT,
        "", payload);
  }

  @Test
  @DisplayName("192 AC-7 the envelope has the fields of API outline §7.1 in order, producer events and the "
      + "eventId")
  void envelopeHasTheFieldsOfSection71() {
    var envelope = event(PAYLOAD).envelope();

    assertThat(envelope.keySet()).containsExactly("messageId", "type", "schemaVersion", "occurredAt", "producer",
        "eventId", "actorUserId", "payload");
    assertThat(envelope).containsEntry("messageId", MESSAGE.toString())
        .containsEntry("type", "EventChanged")
        .containsEntry("schemaVersion", 1)
        .containsEntry("occurredAt", "2026-11-17T10:00:00.123456Z")
        .containsEntry("producer", "events")
        .containsEntry("eventId", EVENT.toString())
        .containsEntry("actorUserId", "");
    assertThat(envelope.get("payload")).isEqualTo(PAYLOAD);
  }

  @Test
  @DisplayName("192 AC-7 an outgoing event needs a message id, an event id and an actor")
  void needsMessageIdEventIdAndActor() {
    assertThatThrownBy(() -> new OutgoingEvent(null, OutgoingEvent.EXCHANGE, "x.y", "XY", 1, AT, EVENT, "", PAYLOAD))
        .isInstanceOf(NullPointerException.class).hasMessage("messageId");
    assertThatThrownBy(() -> new OutgoingEvent(MESSAGE, OutgoingEvent.EXCHANGE, "x.y", "XY", 1, AT, null, "", PAYLOAD))
        .isInstanceOf(NullPointerException.class).hasMessage("eventId");
    assertThatThrownBy(() -> new OutgoingEvent(MESSAGE, OutgoingEvent.EXCHANGE, "x.y", "XY", 1, AT, EVENT, null, PAYLOAD))
        .isInstanceOf(NullPointerException.class).hasMessage("actorUserId");
    assertThat(event(PAYLOAD).actorUserId()).isEmpty();
  }

  @Test
  @DisplayName("192 AC-7 Events publishes to farol.events as producer events, and the payload is a copy")
  void exchangeProducerAndPayloadCopy() {
    assertThat(OutgoingEvent.EXCHANGE).isEqualTo("farol.events");
    assertThat(OutgoingEvent.PRODUCER).isEqualTo("events");
    var source = new HashMap<>(PAYLOAD);
    var event = event(source);
    source.put("name", "changed afterwards");
    source.put("extra", "x");

    assertThat(event.messageId()).isEqualTo(MESSAGE);
    assertThat(event.exchange()).isEqualTo("farol.events");
    assertThat(event.routingKey()).isEqualTo("event.changed");
    assertThat(event.type()).isEqualTo("EventChanged");
    assertThat(event.schemaVersion()).isEqualTo(1);
    assertThat(event.occurredAt()).isEqualTo(AT);
    assertThat(event.eventId()).isEqualTo(EVENT);
    assertThat(event.actorUserId()).isEmpty();
    assertThat(event.payload()).isEqualTo(PAYLOAD);
    assertThatThrownBy(() -> event.payload().put("x", "y")).isInstanceOf(UnsupportedOperationException.class);
  }
}
