package pt.isep.labdsof.reports.application;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import pt.isep.labdsof.reports.domain.HelpRequest;

/** An event to publish, with the envelope fields of API outline §7.1. */
public record OutgoingEvent(
    UUID messageId,
    String exchange,
    String routingKey,
    String type,
    int schemaVersion,
    Instant occurredAt,
    UUID eventId,
    String actorUserId,
    Map<String, Object> payload) {

  public static final String EXCHANGE = "farol.reports";
  public static final String PRODUCER = "reports";

  /** Every Reports event belongs to a Farol event; the actor is empty when the system caused it (§7.1). */
  public OutgoingEvent {
    Objects.requireNonNull(messageId, "messageId");
    Objects.requireNonNull(eventId, "eventId");
    Objects.requireNonNull(actorUserId, "actorUserId");
    payload = Map.copyOf(payload);
  }

  /** Carries only the requestId: no help-request text in the broker (API outline §7.3, M-5, T11). */
  public static OutgoingEvent helpRequestSubmitted(UUID messageId, HelpRequest request) {
    return new OutgoingEvent(messageId, EXCHANGE, "help-request.submitted", "HelpRequestSubmitted", 1,
        request.submittedAt(), request.eventId(), request.requesterId(),
        Map.of("requestId", request.id().toString()));
  }
}
