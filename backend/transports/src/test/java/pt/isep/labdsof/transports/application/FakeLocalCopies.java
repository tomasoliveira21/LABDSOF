package pt.isep.labdsof.transports.application;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import pt.isep.labdsof.transports.domain.EventCopy;
import pt.isep.labdsof.transports.domain.Membership;
import pt.isep.labdsof.transports.domain.Team;

/** The copies of one user in one event, and some teams, for the use cases that only read them. */
final class FakeLocalCopies implements LocalCopies {

  Optional<Membership> membership = Optional.empty();
  Optional<EventCopy> event = Optional.empty();
  final Map<UUID, Team> teams = new HashMap<>();

  @Override
  public Optional<Membership> membership(UUID eventId, UUID userId) {
    return membership.filter(m -> m.eventId().equals(eventId) && m.userId().equals(userId));
  }

  @Override
  public Optional<EventCopy> event(UUID eventId) {
    return event.filter(e -> e.eventId().equals(eventId));
  }

  @Override
  public Optional<Team> team(UUID teamId) {
    return Optional.ofNullable(teams.get(teamId));
  }

  @Override
  public boolean upsertIfNewer(Membership m) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean upsertIfNewer(EventCopy e) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean upsertIfNewer(Team t) {
    throw new UnsupportedOperationException();
  }
}
