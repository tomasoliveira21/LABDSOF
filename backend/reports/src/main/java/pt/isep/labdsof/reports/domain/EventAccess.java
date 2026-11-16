package pt.isep.labdsof.reports.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * What one user may do in one event, decided from the local copies only (ADR-006 decision 4).
 * No copy, a Removed membership, or a team of another event means no access.
 */
public final class EventAccess {

  private final Membership membership;
  private final List<Team> teams;

  private EventAccess(Membership membership, List<Team> teams) {
    this.membership = membership;
    this.teams = teams;
  }

  /** Empty when the user is not an active member of the event. */
  public static Optional<EventAccess> of(Membership membership, Collection<Team> teams) {
    if (membership == null || membership.status() != Membership.Status.Active) {
      return Optional.empty();
    }
    var ownTeams = teams.stream()
        .filter(team -> team.eventId().equals(membership.eventId()))
        .filter(team -> membership.teamIds().contains(team.teamId()))
        .toList();
    return Optional.of(new EventAccess(membership, ownTeams));
  }

  public String userId() {
    return membership.userId();
  }

  /** An EventAdmin has every permission of the event; a RegularUser those of their teams. */
  public boolean has(Permission permission) {
    return membership.role() == Membership.Role.EventAdmin
        || teams.stream().anyMatch(team -> team.grants(permission));
  }
}
