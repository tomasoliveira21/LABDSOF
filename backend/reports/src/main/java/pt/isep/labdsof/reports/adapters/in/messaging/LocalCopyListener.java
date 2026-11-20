package pt.isep.labdsof.reports.adapters.in.messaging;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import pt.isep.labdsof.reports.application.LocalCopyService;
import pt.isep.labdsof.reports.domain.Category;
import pt.isep.labdsof.reports.domain.Membership;
import pt.isep.labdsof.reports.domain.Permission;
import pt.isep.labdsof.reports.domain.Team;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Consumes MembershipChanged (Auth & Users) and TeamChanged (Events) into the local copies (spec D7).
 * An invalid message goes straight to the dead-letter queue (spec D8); other failures are retried first.
 */
@Component
class LocalCopyListener {

  private static final Logger log = LoggerFactory.getLogger(LocalCopyListener.class);

  /** The envelope of API outline §7.1. */
  record Envelope(UUID messageId, String type, Integer schemaVersion, Instant occurredAt, String producer,
      UUID eventId, String actorUserId, JsonNode payload) {}

  record MembershipPayload(String userId, Membership.Role role, Membership.Status status,
      Set<UUID> teamIds, Long version) {}

  record TeamPayload(UUID teamId, String name, List<PermissionPayload> permissions,
      Set<Category> handledCategories, Boolean deleted, Long version) {}

  record PermissionPayload(String permission, Set<String> reportTypes) {}

  private final LocalCopyService copies;
  private final DeadLetters deadLetters;
  private final JsonMapper json;

  LocalCopyListener(LocalCopyService copies, DeadLetters deadLetters, JsonMapper json) {
    this.copies = copies;
    this.deadLetters = deadLetters;
    // Producers may add fields (contracts evolve by adding): tolerate unknown ones, unlike the REST API.
    this.json = json.rebuild().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
  }

  @RabbitListener(queues = "reports.auth-users-events")
  void onAuthUsersEvent(Message message) {
    handle(message);
  }

  @RabbitListener(queues = "reports.events-events")
  void onEventsEvent(Message message) {
    handle(message);
  }

  /** #148: the producer's AMQP correlation id, when it sent one, is in every log line of this message. */
  private void handle(Message message) {
    var correlationId = message.getMessageProperties().getCorrelationId();
    if (correlationId != null) {
      MDC.put("correlationId", correlationId);
    }
    try {
      apply(message);
    } finally {
      MDC.remove("correlationId");
    }
  }

  private void apply(Message message) {
    Envelope envelope;
    Object copy;
    try {
      envelope = json.readValue(message.getBody(), Envelope.class);
      if (envelope == null || envelope.messageId() == null || envelope.type() == null
          || envelope.occurredAt() == null
          || envelope.producer() == null || envelope.actorUserId() == null) {
        throw new IllegalArgumentException("messageId, type, occurredAt, producer and actorUserId are required");
      }
      if (!Integer.valueOf(1).equals(envelope.schemaVersion())) {
        throw new IllegalArgumentException("schemaVersion " + envelope.schemaVersion() + " is not supported");
      }
      copy = switch (envelope.type()) {
        case "MembershipChanged" -> membership(envelope);
        case "TeamChanged" -> team(envelope);
        default -> null;
      };
    } catch (JacksonException | IllegalArgumentException e) {
      deadLetters.recover(message, e); // invalid: retrying cannot help; a bug in our code (NPE…) is retried
      return;
    }
    if (copy == null) {
      log.info("Event {} of type {} is not handled by Reports yet; acknowledged",
          envelope.messageId(), envelope.type());
      return;
    }
    var outcome = copy instanceof Membership m
        ? copies.apply(envelope.messageId(), m)
        : copies.apply(envelope.messageId(), (Team) copy);
    log.debug("Event {} ({}): {}", envelope.messageId(), envelope.type(), outcome);
  }

  private Membership membership(Envelope envelope) {
    var p = payload(envelope, MembershipPayload.class);
    return new Membership(required(envelope.eventId(), "eventId"), p.userId(), required(p.role(), "role"),
        required(p.status(), "status"), items(p.teamIds(), "teamIds"),
        required(p.version(), "version"));
  }

  private Team team(Envelope envelope) {
    var p = payload(envelope, TeamPayload.class);
    var permissions = items(p.permissions(), "permissions").stream()
        .map(permission -> new Team.TeamPermission(
            Permission.fromWireName(required(permission.permission(), "permission")),
            permission.reportTypes() == null ? Set.of() : items(permission.reportTypes(), "reportTypes")))
        .toList();
    return new Team(required(p.teamId(), "teamId"), required(envelope.eventId(), "eventId"), p.name(),
        permissions, items(p.handledCategories(), "handledCategories"),
        required(p.deleted(), "deleted"), required(p.version(), "version"));
  }

  private static <T> T required(T value, String name) {
    if (value == null) {
      throw new IllegalArgumentException(name + " is required");
    }
    return value;
  }

  private <T> T payload(Envelope envelope, Class<T> type) {
    return required(json.treeToValue(required(envelope.payload(), "payload"), type), "payload");
  }

  /** A required JSON array without null items (Jackson builds mutable collections). */
  private static <C extends Collection<?>> C items(C value, String name) {
    if (required(value, name).contains(null)) {
      throw new IllegalArgumentException(name + " must not contain null");
    }
    return value;
  }
}
