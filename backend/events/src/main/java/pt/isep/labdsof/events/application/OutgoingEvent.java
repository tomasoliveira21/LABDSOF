package pt.isep.labdsof.events.application;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * An event with the envelope of API outline §7.1. Every Events event belongs to an event (ADR-003), so
 * {@code eventId} is required.
 */
public record OutgoingEvent(UUID messageId, String exchange, String routingKey, String type, int schemaVersion,
    Instant occurredAt, UUID eventId, String actorUserId, Map<String, Object> payload) {

  public static final String EXCHANGE = "farol.events";
  public static final String PRODUCER = "events";

  public OutgoingEvent {
    Objects.requireNonNull(messageId, "messageId");
    Objects.requireNonNull(eventId, "eventId");
    Objects.requireNonNull(actorUserId, "actorUserId");
    payload = Map.copyOf(payload);
  }

  /** The message body, in the order of §7.1. */
  public Map<String, Object> envelope() {
    var envelope = new LinkedHashMap<String, Object>();
    envelope.put("messageId", messageId.toString());
    envelope.put("type", type);
    envelope.put("schemaVersion", schemaVersion);
    envelope.put("occurredAt", occurredAt.toString());
    envelope.put("producer", PRODUCER);
    envelope.put("eventId", eventId.toString());
    envelope.put("actorUserId", actorUserId);
    envelope.put("payload", payload);
    return envelope;
  }
}
