package pt.isep.labdsof.reports.domain;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Local copy of a user's membership of an event, from MembershipChanged (API outline §7.4). */
public record Membership(UUID eventId, String userId, Role role, Status status, Set<UUID> teamIds,
    long version) {

  // The constants are the contract values (API, events, database, JSON Schemas), so they keep its PascalCase.
  @SuppressWarnings("java:S115")
  public enum Role { EventAdmin, RegularUser }

  @SuppressWarnings("java:S115") // as Role
  public enum Status { Active, Removed }

  public Membership {
    Objects.requireNonNull(eventId, "eventId");
    Objects.requireNonNull(role, "role");
    Objects.requireNonNull(status, "status");
    if (userId == null || userId.isBlank()) {
      throw new IllegalArgumentException("userId is required");
    }
    if (version < 1) {
      throw new IllegalArgumentException("version must be at least 1");
    }
    teamIds = Set.copyOf(teamIds);
  }
}
