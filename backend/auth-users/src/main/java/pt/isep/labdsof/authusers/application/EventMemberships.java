package pt.isep.labdsof.authusers.application;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import pt.isep.labdsof.authusers.domain.EventMembership;
import pt.isep.labdsof.authusers.domain.EventRole;

/** Port: the memberships of an event and the teams each member is in (US-18, API outline §2). */
public interface EventMemberships {

  /** One member of the event as it reads now, with its teams; empty when there is no such membership. */
  Optional<EventMembership> find(UUID eventId, UUID userId);

  /**
   * The same read, with the membership's row locked for the rest of the transaction (spec D7), so two changes of
   * one member queue instead of both writing the same version.
   */
  Optional<EventMembership> lockForUpdate(UUID eventId, UUID userId);

  /**
   * Stores a membership that is not there yet and answers true when it did (US-12 D5): a second join of the same
   * event stores nothing, and two joins at once leave one row, because the key serialises them.
   */
  boolean insertIfAbsent(EventMembership membership, UUID actorUserId, Instant at);

  /** Stores the assignment and the membership's new version, with who made the change and when (D6, D8). */
  void addToTeam(EventMembership updated, UUID teamId, UUID actorUserId, Instant at);

  /** Removes the assignment and stores the membership's new version (D6, D8). */
  void removeFromTeam(EventMembership updated, UUID teamId, UUID actorUserId, Instant at);

  /**
   * Removes an assignment without touching the membership's version, which is what a {@code DELETE} of a team
   * Events has already deleted does: the team is not in the member's teams any more (nothing to publish), but its
   * row is still there and this is the only call that can clear it (review of #255).
   */
  void clearAssignment(UUID eventId, UUID userId, UUID teamId);

  /** The event's members for the back office (D10); {@code role} and {@code teamId} are optional filters. */
  List<Member> list(UUID eventId, EventRole role, UUID teamId);

  /** A member as the list shows them: the display name comes from the account, the teams from the assignments. */
  record Member(UUID userId, String displayName, EventRole role, List<UUID> teamIds) {}
}
