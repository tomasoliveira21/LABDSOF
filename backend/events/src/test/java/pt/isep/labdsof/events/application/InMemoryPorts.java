package pt.isep.labdsof.events.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import pt.isep.labdsof.events.domain.Event;
import pt.isep.labdsof.events.domain.EventStatus;
import pt.isep.labdsof.events.domain.Membership;
import pt.isep.labdsof.events.domain.Team;
import pt.isep.labdsof.events.domain.TeamPermission;

/** Fakes of the ports, so the use cases are tested without a database. */
class InMemoryPorts implements Events, LocalCopies, ProcessedMessages, AccessAudit, Teams, Outbox,
    TeamAudit, JoinCodes, EventAudit {

  record Refused(UUID userId, UUID eventId, Instant at, Refusal refusal) {}

  /** A publish as US-11 D7 records it. */
  record Published(UUID userId, UUID eventId, Instant at, EventStatus status, long version) {}

  /** Who published an event and when, as the event table's updated_by and updated_at record it. */
  record EventWrite(UUID eventId, UUID actor, Instant at, long version) {}

  /** Who wrote a team and when, as the team table's updated_by and updated_at record it. */
  record Write(UUID teamId, UUID actor, Instant at, long version) {}

  /** A permission change as US-17 AC-4 records it. */
  record Audited(UUID userId, UUID eventId, UUID teamId, Instant at, List<TeamPermission> permissions) {}

  final Map<UUID, Event> events = new HashMap<>();
  final Map<List<UUID>, Membership> memberships = new HashMap<>();
  final Set<UUID> processed = new HashSet<>();
  final List<Refused> refusals = new ArrayList<>();
  final Map<UUID, Team> teams = new LinkedHashMap<>();
  final List<Write> writes = new ArrayList<>();
  final List<EventWrite> eventWrites = new ArrayList<>();
  final List<OutgoingEvent> published = new ArrayList<>();
  final List<Audited> audited = new ArrayList<>();

  /** Set to simulate another request deleting the team between the read and the write (US-16 D3). */
  boolean teamVanishesOnWrite;

  /** Set to simulate another request changing the team, which is still live, between the read and the write. */
  boolean teamChangesOnWrite;

  /** The one join code {@link #next()} hands out, so the published event is predictable. */
  static final String JOIN_CODE = "JOIN-CODE-1";

  /** The publishes recorded by the {@link EventAudit} port. */
  final List<Published> publishes = new ArrayList<>();

  /**
   * Set to the outcome the adapter reports for a publish that matched no row, so the answer the use case gives to
   * each one can be tested: another request published the event meanwhile ({@code NOT_DRAFT}), published it from
   * the same read ({@code CONFLICT}), or there is no such event ({@code GONE}). None of them is reachable with a
   * sequential request, which is why the real outcomes live in {@code JdbcEventsIT} (US-11 D4).
   */
  Events.Write publishOutcome;

  @Override
  public Optional<Event> findById(UUID eventId) {
    return Optional.ofNullable(events.get(eventId));
  }

  @Override
  public String next() {
    return JOIN_CODE;
  }

  @Override
  public void published(UUID userId, UUID eventId, Instant at, EventStatus status, long version) {
    publishes.add(new Published(userId, eventId, at, status, version));
  }

  /** The guard of the real adapter: the row must still be the draft the use case read (US-11 D4). */
  @Override
  public Events.Write publish(Event event, UUID actor, Instant at) {
    if (publishOutcome != null) {
      return publishOutcome;
    }
    var stored = events.get(event.eventId());
    if (stored == null) {
      return Events.Write.GONE;
    }
    if (stored.status() != EventStatus.Draft) {
      return Events.Write.NOT_DRAFT;
    }
    if (stored.version() != event.version() - 1) {
      return Events.Write.CONFLICT;
    }
    events.put(event.eventId(), event);
    eventWrites.add(new EventWrite(event.eventId(), actor, at, event.version()));
    return Events.Write.STORED;
  }

  @Override
  public Optional<Membership> membership(UUID eventId, UUID userId) {
    return Optional.ofNullable(memberships.get(List.of(eventId, userId)));
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
  public boolean markProcessed(UUID messageId) {
    return processed.add(messageId);
  }

  @Override
  public void refused(UUID userId, UUID eventId, Instant at, Refusal refusal) {
    refusals.add(new Refused(userId, eventId, at, refusal));
  }

  @Override
  public List<Team> liveOfEvent(UUID eventId) {
    return teams.values().stream()
        .filter(team -> team.eventId().equals(eventId) && !team.deleted())
        .sorted(Comparator.comparing(team -> team.name().toLowerCase(Locale.ROOT)))
        .toList();
  }

  @Override
  public Optional<Team> liveOfEvent(UUID eventId, UUID teamId) {
    return liveOfEvent(eventId).stream().filter(team -> team.teamId().equals(teamId)).findFirst();
  }

  @Override
  public Teams.Write add(Team team, UUID actor, Instant at) {
    return store(team, actor, at);
  }

  @Override
  public Teams.Write replace(Team team, UUID actor, Instant at) {
    return store(team, actor, at);
  }

  @Override
  public Teams.Write delete(Team team, UUID actor, Instant at) {
    if (teamVanishesOnWrite) {
      return Teams.Write.GONE;
    }
    if (teamChangesOnWrite || stale(team)) {
      return Teams.Write.CONFLICT;
    }
    teams.put(team.teamId(), team);
    writes.add(new Write(team.teamId(), actor, at, team.version()));
    return Teams.Write.STORED;
  }

  @Override
  public Teams.Write replacePermissions(Team team, UUID actor, Instant at) {
    return store(team, actor, at);
  }

  @Override
  public void add(OutgoingEvent event) {
    published.add(event);
  }

  @Override
  public void permissionsReplaced(UUID userId, UUID eventId, UUID teamId, Instant at,
      List<TeamPermission> permissions) {
    audited.add(new Audited(userId, eventId, teamId, at, permissions));
  }

  /** As team_name_per_event: the name is free among the live teams of the event, this one apart. */
  private Teams.Write store(Team team, UUID actor, Instant at) {
    var taken = liveOfEvent(team.eventId()).stream()
        .anyMatch(other -> !other.teamId().equals(team.teamId()) && other.isNamed(team.name()));
    if (taken) {
      return Teams.Write.NAME_TAKEN;
    }
    if (teamVanishesOnWrite) {
      return Teams.Write.GONE;
    }
    if (teamChangesOnWrite || stale(team)) {
      return Teams.Write.CONFLICT;
    }
    teams.put(team.teamId(), team);
    writes.add(new Write(team.teamId(), actor, at, team.version()));
    return Teams.Write.STORED;
  }

  /**
   * The version guard of the real adapter: a write only lands on the row it read, so a second write from the same
   * read is refused instead of publishing a version twice (US-16 D3).
   */
  private boolean stale(Team team) {
    var current = teams.get(team.teamId());
    return current != null && current.version() != team.version() - 1;
  }
}
