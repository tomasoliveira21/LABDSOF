package pt.isep.labdsof.transports.adapters.in.messaging;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.event.Level;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import pt.isep.labdsof.transports.Correlation;
import pt.isep.labdsof.transports.application.LocalCopyService;
import pt.isep.labdsof.transports.application.LocalCopyService.Outcome;
import pt.isep.labdsof.transports.domain.EventCopy;
import pt.isep.labdsof.transports.domain.EventStatus;
import pt.isep.labdsof.transports.domain.Membership;
import pt.isep.labdsof.transports.domain.Permission;
import pt.isep.labdsof.transports.domain.Team;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Consumes Transports' two queues into the local copies (spec D3): MembershipChanged, EventChanged and
 * TeamChanged are stored; the other types are acknowledged and logged until their stories. An invalid message
 * goes straight to the dead-letter queue; other failures are retried first.
 */
@Component
class LocalCopyListener {

  static final String QUEUE_AUTH_USERS = "transports.auth-users-events";
  static final String QUEUE_EVENTS = "transports.events-events";
  static final String MEMBERSHIP_CHANGED = "MembershipChanged";
  static final String EVENT_CHANGED = "EventChanged";
  static final String TEAM_CHANGED = "TeamChanged";

  private static final Logger log = LoggerFactory.getLogger(LocalCopyListener.class);
  private static final String MESSAGE_ID = "messageId";
  private static final String TYPE = "type";

  /** The envelope of API outline 7.1. */
  record Envelope(UUID messageId, String type, Integer schemaVersion, Instant occurredAt, String producer,
      UUID eventId, String actorUserId, JsonNode payload) {}

  /** A missing status is read as Active (spec D3), in case #198 drops MembershipStatus. */
  record MembershipPayload(UUID userId, Membership.Role role, Membership.Status status, Set<UUID> teamIds,
      Long version) {}

  record EventPayload(EventStatus status, Long version) {}

  record TeamPayload(UUID teamId, List<TeamPermission> permissions, Boolean deleted, Long version) {}

  record TeamPermission(String permission) {}

  private final LocalCopyService copies;
  private final DeadLetters deadLetters;
  private final JsonMapper json;

  LocalCopyListener(LocalCopyService copies, DeadLetters deadLetters, JsonMapper json) {
    this.copies = copies;
    this.deadLetters = deadLetters;
    // Producers may add fields (contracts evolve by adding): tolerate unknown ones, unlike the REST API.
    this.json = json.rebuild().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
  }

  @RabbitListener(queues = QUEUE_AUTH_USERS)
  void onAuthUsersEvent(Message message) {
    withCorrelationId(message);
  }

  @RabbitListener(queues = QUEUE_EVENTS)
  void onEventsEvent(Message message) {
    withCorrelationId(message);
  }

  private void withCorrelationId(Message message) {
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
    Function<UUID, Outcome> apply;
    try {
      envelope = required(json.readValue(message.getBody(), Envelope.class), "the message");
      if (envelope.messageId() == null || envelope.type() == null || envelope.occurredAt() == null
          || envelope.producer() == null || envelope.actorUserId() == null) {
        throw new IllegalArgumentException("messageId, type, occurredAt, producer and actorUserId are required");
      }
      if (!Integer.valueOf(1).equals(envelope.schemaVersion())) {
        throw new IllegalArgumentException("schemaVersion " + envelope.schemaVersion() + " is not supported");
      }
      apply = switch (envelope.type()) {
        case MEMBERSHIP_CHANGED -> {
          var membership = membership(envelope);
          yield messageId -> copies.apply(messageId, membership);
        }
        case EVENT_CHANGED -> {
          var event = event(envelope);
          yield messageId -> copies.apply(messageId, event);
        }
        case TEAM_CHANGED -> {
          var team = team(envelope);
          yield messageId -> copies.apply(messageId, team);
        }
        default -> null;
      };
    } catch (JacksonException | IllegalArgumentException | NullPointerException e) {
      log.atWarn()
          .addKeyValue(MESSAGE_ID, String.valueOf(message.getMessageProperties().getMessageId()))
          .addKeyValue("reason", DeadLetters.describe(e))
          .log("Invalid message sent to the dead-letter queue");
      deadLetters.recover(message, e); // invalid: retrying cannot help (spec D3)
      return;
    }
    if (apply == null) {
      log.atInfo()
          .addKeyValue(MESSAGE_ID, envelope.messageId().toString())
          .addKeyValue(TYPE, envelope.type())
          .log("Event type not handled by Transports yet; acknowledged"); // spec D3
      return;
    }
    // A database failure propagates: retried, then dead-lettered
    var outcome = apply.apply(envelope.messageId());
    log.atLevel(outcome == Outcome.OTHER_EVENT ? Level.WARN : Level.INFO)
        .addKeyValue(MESSAGE_ID, envelope.messageId().toString())
        .addKeyValue(TYPE, envelope.type())
        .addKeyValue("outcome", outcome.name())
        .log(outcome == Outcome.OTHER_EVENT
            ? "TeamChanged for a team stored under another event; ignored" // #214 D7: a producer bug
            : "Event applied to the local copy");
  }

  private Membership membership(Envelope envelope) {
    var p = json.treeToValue(required(envelope.payload(), "payload"), MembershipPayload.class);
    var status = p.status() == null ? Membership.Status.Active : p.status();
    return new Membership(required(envelope.eventId(), "eventId"), p.userId(), p.role(), status,
        required(p.teamIds(), "teamIds"), required(p.version(), "version"));
  }

  private EventCopy event(Envelope envelope) {
    var p = json.treeToValue(required(envelope.payload(), "payload"), EventPayload.class);
    return new EventCopy(required(envelope.eventId(), "eventId"), p.status(), required(p.version(), "version"));
  }

  private Team team(Envelope envelope) {
    var p = json.treeToValue(required(envelope.payload(), "payload"), TeamPayload.class);
    var permissions = required(p.permissions(), "permissions").stream()
        .map(entry -> Permission.fromWireName(required(entry, "permissions[]").permission()))
        .collect(Collectors.toSet());
    return new Team(p.teamId(), required(envelope.eventId(), "eventId"), permissions,
        required(p.deleted(), "deleted"), required(p.version(), "version"));
  }

  private static <T> T required(T value, String name) {
    if (value == null) {
      throw new IllegalArgumentException(name + " is required");
    }
    return value;
  }
}
