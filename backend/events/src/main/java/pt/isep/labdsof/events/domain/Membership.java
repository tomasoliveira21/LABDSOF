package pt.isep.labdsof.events.domain;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Local copy of MembershipChanged (Auth &amp; Users); {@code userId} is the token's {@code sub}. */
public record Membership(UUID eventId, UUID userId, Role role, Status status, Set<UUID> teamIds, long version) {

  @SuppressWarnings("java:S115")
  public enum Role { EventAdmin, RegularUser }

  @SuppressWarnings("java:S115")
  public enum Status { Active, Removed }

  public Membership {
    Objects.requireNonNull(eventId, "eventId");
    Objects.requireNonNull(userId, "userId");
    Objects.requireNonNull(role, "role");
    Objects.requireNonNull(status, "status");
    if (version < 1) {
      throw new IllegalArgumentException("version must be at least 1");
    }
    teamIds = Set.copyOf(teamIds); // NPE for a null set or a null element: an invalid message
  }

  /** Spec D3: a Removed row is kept so a late older version cannot bring it back, but gives no access. */
  public boolean isActive() {
    return status == Status.Active;
  }

  /** Spec D5 (US-06, US-09, US-13): an EventAdmin sees every status, a RegularUser what participants see. */
  public boolean canSee(EventStatus eventStatus) {
    return role == Role.EventAdmin || eventStatus.visibleToParticipants();
  }
}
