package pt.isep.labdsof.authusers.domain;

import java.util.List;
import java.util.Objects;
import java.util.TreeSet;
import java.util.UUID;

/**
 * One member of one event, with the teams they are in (US-18) and the version every change raises. This is the
 * state {@code MembershipChanged} carries in full (API outline §7.3, spec D6): Events, Reports and Transports keep
 * a copy of it and decide from the copy what the member may do, which is why a team assignment is a change of the
 * <em>membership</em> and not of a list on its own.
 *
 * <p>A removed member has no row at all (proposal of 15 November 2026 §4), so there is no status here.
 */
public record EventMembership(UUID eventId, UUID userId, EventRole role, List<UUID> teamIds, long version) {

  public EventMembership {
    Objects.requireNonNull(eventId, "eventId");
    Objects.requireNonNull(userId, "userId");
    Objects.requireNonNull(role, "role");
    if (version < 1) {
      throw new IllegalArgumentException("A membership's version starts at 1");
    }
    // Sorted and without duplicates: the order of the team ids is published, and two copies of one team mean
    // nothing to a consumer that keeps a set
    teamIds = List.copyOf(new TreeSet<>(Objects.requireNonNull(teamIds, "teamIds")));
  }

  public boolean isEventAdmin() {
    return role == EventRole.EventAdmin;
  }

  public boolean isIn(UUID teamId) {
    return teamIds.contains(teamId);
  }

  /** This membership with the team added and the next version; the same membership when it is already in it (D3). */
  public EventMembership withTeam(UUID teamId) {
    Objects.requireNonNull(teamId, "teamId");
    if (isIn(teamId)) {
      return this;
    }
    var teams = new TreeSet<>(teamIds);
    teams.add(teamId);
    return new EventMembership(eventId, userId, role, List.copyOf(teams), version + 1);
  }

  /** This membership without the team and with the next version; the same membership when it is not in it (D3). */
  public EventMembership withoutTeam(UUID teamId) {
    if (!isIn(teamId)) {
      return this;
    }
    var teams = new TreeSet<>(teamIds);
    teams.remove(teamId);
    return new EventMembership(eventId, userId, role, List.copyOf(teams), version + 1);
  }
}
