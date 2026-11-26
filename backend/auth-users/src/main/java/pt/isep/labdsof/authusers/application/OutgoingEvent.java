package pt.isep.labdsof.authusers.application;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import pt.isep.labdsof.authusers.domain.EventMembership;
import pt.isep.labdsof.authusers.domain.GlobalRole;
import pt.isep.labdsof.authusers.domain.UserAccount;

/** An event with the envelope of API outline §7.1. {@code eventId} is null: these events belong to no event. */
public record OutgoingEvent(UUID messageId, String exchange, String routingKey, String type, int schemaVersion,
    Instant occurredAt, UUID eventId, String actorUserId, Map<String, Object> payload) {

  public static final String EXCHANGE = "farol.auth-users";
  public static final String PRODUCER = "auth-users";

  public OutgoingEvent {
    Objects.requireNonNull(messageId, "messageId");
    Objects.requireNonNull(actorUserId, "actorUserId");
    payload = Map.copyOf(payload);
  }

  public static OutgoingEvent userProfileChanged(UUID messageId, UserAccount account, String actorUserId) {
    return new OutgoingEvent(messageId, EXCHANGE, "user-profile.changed", "UserProfileChanged", 1,
        account.updatedAt(), null, actorUserId,
        Map.of("userId", account.userId().toString(), "displayName", account.displayName(),
            "version", account.version()));
  }

  public static OutgoingEvent globalRoleChanged(UUID messageId, UUID userId, GlobalRole role, boolean granted,
      long version, Instant occurredAt, String actorUserId) {
    return new OutgoingEvent(messageId, EXCHANGE, "global-role.changed", "GlobalRoleChanged", 1,
        occurredAt, null, actorUserId,
        Map.of("userId", userId.toString(), "role", role.name(), "granted", granted, "version", version));
  }

  /**
   * The full state of one membership (API outline §7.3, US-18 D6): Events, Reports and Transports upsert their
   * copy from it and ignore a version that is not newer, so every change sends every team the member is in, not
   * the one that changed. {@code status} is sent as {@code Active} while {@code MembershipStatus} stands (#198):
   * a membership that no longer exists is a row this service deletes, not a status it publishes.
   */
  public static OutgoingEvent membershipChanged(UUID messageId, EventMembership membership, Instant occurredAt,
      String actorUserId) {
    return new OutgoingEvent(messageId, EXCHANGE, "membership.changed", "MembershipChanged", 1,
        occurredAt, membership.eventId(), actorUserId,
        Map.of("userId", membership.userId().toString(), "role", membership.role().name(), "status", "Active",
            "teamIds", membership.teamIds().stream().map(UUID::toString).toList(),
            "version", membership.version()));
  }

  /** The message body, in the order of §7.1. */
  public Map<String, Object> envelope() {
    var envelope = new LinkedHashMap<String, Object>();
    envelope.put("messageId", messageId.toString());
    envelope.put("type", type);
    envelope.put("schemaVersion", schemaVersion);
    envelope.put("occurredAt", occurredAt.toString());
    envelope.put("producer", PRODUCER);
    envelope.put("eventId", eventId == null ? null : eventId.toString());
    envelope.put("actorUserId", actorUserId);
    envelope.put("payload", payload);
    return envelope;
  }
}
