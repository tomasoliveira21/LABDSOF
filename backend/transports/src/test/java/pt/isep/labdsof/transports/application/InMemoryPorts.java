package pt.isep.labdsof.transports.application;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import pt.isep.labdsof.transports.domain.EventCopy;
import pt.isep.labdsof.transports.domain.Membership;
import pt.isep.labdsof.transports.domain.Team;

/** In-memory ports with the same rules as the SQL adapters, for the unit tests of the use cases. */
final class InMemoryPorts implements LocalCopies, ProcessedMessages {

  private final Set<UUID> processed = new HashSet<>();
  private final Map<List<UUID>, Membership> memberships = new HashMap<>();
  private final Map<UUID, EventCopy> events = new HashMap<>();
  private final Map<UUID, Team> teams = new HashMap<>();

  @Override
  public boolean markProcessed(UUID messageId) {
    return processed.add(messageId);
  }

  @Override
  public Optional<Membership> membership(UUID eventId, UUID userId) {
    return Optional.ofNullable(memberships.get(List.of(eventId, userId)));
  }

  @Override
  public Optional<EventCopy> event(UUID eventId) {
    return Optional.ofNullable(events.get(eventId));
  }

  @Override
  public Optional<Team> team(UUID teamId) {
    return Optional.ofNullable(teams.get(teamId));
  }

  @Override
  public boolean upsertIfNewer(Membership membership) {
    var key = List.of(membership.eventId(), membership.userId());
    var stored = memberships.get(key);
    if (stored != null && stored.version() >= membership.version()) {
      return false;
    }
    memberships.put(key, membership);
    return true;
  }

  @Override
  public boolean upsertIfNewer(EventCopy event) {
    var stored = events.get(event.eventId());
    if (stored != null && stored.version() >= event.version()) {
      return false;
    }
    events.put(event.eventId(), event);
    return true;
  }

  @Override
  public boolean upsertIfNewer(Team team) {
    var stored = teams.get(team.teamId());
    if (stored != null && (stored.version() >= team.version() || !stored.eventId().equals(team.eventId()))) {
      return false;
    }
    teams.put(team.teamId(), team);
    return true;
  }
}
