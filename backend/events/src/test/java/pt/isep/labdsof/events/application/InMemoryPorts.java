package pt.isep.labdsof.events.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import pt.isep.labdsof.events.domain.Event;
import pt.isep.labdsof.events.domain.Membership;

/** Fakes of the ports, so the use cases are tested without a database. */
class InMemoryPorts implements Events, LocalCopies, ProcessedMessages, AccessAudit {

  record Refused(UUID userId, UUID eventId, Instant at, Refusal refusal) {}

  final Map<UUID, Event> events = new HashMap<>();
  final Map<List<UUID>, Membership> memberships = new HashMap<>();
  final Set<UUID> processed = new HashSet<>();
  final List<Refused> refusals = new ArrayList<>();

  @Override
  public Optional<Event> findById(UUID eventId) {
    return Optional.ofNullable(events.get(eventId));
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
}
