package pt.isep.labdsof.events.adapters.in.messaging;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import pt.isep.labdsof.events.Correlation;
import pt.isep.labdsof.events.application.LocalCopyService;
import pt.isep.labdsof.events.domain.Membership;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Consumes the events of Auth &amp; Users into the membership copy (spec D2). Only MembershipChanged is
 * stored; the other types are acknowledged and logged until their stories. An invalid message goes
 * straight to the dead-letter queue; other failures are retried first.
 */
@Component
class LocalCopyListener {

  static final String QUEUE = "events.auth-users-events";
  static final String MEMBERSHIP_CHANGED = "MembershipChanged";

  private static final Logger log = LoggerFactory.getLogger(LocalCopyListener.class);

  /** The envelope of API outline §7.1. */
  record Envelope(UUID messageId, String type, Integer schemaVersion, Instant occurredAt, String producer,
      UUID eventId, String actorUserId, JsonNode payload) {}

  /** A missing status is read as Active (spec §4), in case #198 drops MembershipStatus. */
  record MembershipPayload(UUID userId, Membership.Role role, Membership.Status status, Set<UUID> teamIds,
      Long version) {}

  private final LocalCopyService copies;
  private final DeadLetters deadLetters;
  private final JsonMapper json;

  LocalCopyListener(LocalCopyService copies, DeadLetters deadLetters, JsonMapper json) {
    this.copies = copies;
    this.deadLetters = deadLetters;
    // Producers may add fields (contracts evolve by adding): tolerate unknown ones, unlike the REST API.
    this.json = json.rebuild().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
  }

  @RabbitListener(queues = QUEUE)
  void onAuthUsersEvent(Message message) {
    var correlationId = message.getMessageProperties().getCorrelationId();
    if (correlationId != null) {
      MDC.put(Correlation.MDC_KEY, correlationId);
    }
    try {
      handle(message);
    } finally {
      MDC.remove(Correlation.MDC_KEY);
    }
  }

  private void handle(Message message) {
    Envelope envelope;
    Membership membership;
    try {
      envelope = json.readValue(message.getBody(), Envelope.class);
      if (envelope.messageId() == null || envelope.type() == null || envelope.occurredAt() == null
          || envelope.producer() == null || envelope.actorUserId() == null) {
        throw new IllegalArgumentException("messageId, type, occurredAt, producer and actorUserId are required");
      }
      if (!Integer.valueOf(1).equals(envelope.schemaVersion())) {
        throw new IllegalArgumentException("schemaVersion " + envelope.schemaVersion() + " is not supported");
      }
      membership = MEMBERSHIP_CHANGED.equals(envelope.type()) ? membership(envelope) : null;
    } catch (JacksonException | IllegalArgumentException | NullPointerException e) {
      log.atWarn()
          .addKeyValue("messageId", String.valueOf(message.getMessageProperties().getMessageId()))
          .addKeyValue("reason", DeadLetters.describe(e))
          .log("Invalid message sent to the dead-letter queue");
      deadLetters.recover(message, e); // invalid: retrying cannot help (spec D2)
      return;
    }
    if (membership == null) {
      log.atInfo()
          .addKeyValue("messageId", envelope.messageId().toString())
          .addKeyValue("type", envelope.type())
          .log("Event type not handled by Events yet; acknowledged"); // spec D2
      return;
    }
    // A database failure propagates: retried, then dead-lettered
    var outcome = copies.apply(envelope.messageId(), membership);
    log.atInfo()
        .addKeyValue("messageId", envelope.messageId().toString())
        .addKeyValue("type", envelope.type())
        .addKeyValue("outcome", outcome.name())
        .log("MembershipChanged applied to the membership copy");
  }

  private Membership membership(Envelope envelope) {
    var p = json.treeToValue(required(envelope.payload(), "payload"), MembershipPayload.class);
    var status = p.status() == null ? Membership.Status.Active : p.status();
    return new Membership(required(envelope.eventId(), "eventId"), p.userId(), p.role(), status,
        required(p.teamIds(), "teamIds"), required(p.version(), "version"));
  }

  private static <T> T required(T value, String name) {
    if (value == null) {
      throw new IllegalArgumentException(name + " is required");
    }
    return value;
  }
}
