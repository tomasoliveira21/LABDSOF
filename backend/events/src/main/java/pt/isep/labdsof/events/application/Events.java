package pt.isep.labdsof.events.application;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import pt.isep.labdsof.events.domain.Event;

/** Port: the events of this service's own database. */
public interface Events {

  /**
   * What a write did. As every write of this service (US-16 D3, decision 3 of 21 November 2026), the statement
   * carries the version it read, and the publish its status too: {@code NOT_DRAFT} means the row moved on and is
   * no longer a draft, {@code CONFLICT} that it is still a draft at another version, and {@code GONE} that there
   * is no such event. None of them writes anything.
   */
  enum Write { STORED, NOT_DRAFT, CONFLICT, GONE }

  Optional<Event> findById(UUID eventId);

  /**
   * Stores the published event — its status, its join code and its next version — only if the row is still the
   * draft the use case read (US-11 D4).
   */
  Write publish(Event event, UUID actor, Instant at);
}
