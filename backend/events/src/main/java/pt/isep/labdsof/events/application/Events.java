package pt.isep.labdsof.events.application;

import java.time.Instant;
import java.util.List;
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

  /** Stores a new draft (US-06). Its id is the use case's, so no key is contended and two creates never wait. */
  void insert(Event event, UUID actor, Instant at);

  /**
   * Every event with the five fields {@code features.md} §4.1 allows the FarolAdmin, newest first (US-07). The
   * EventAdmins come from the membership copy and are empty for an event that has none yet (US-06 D12).
   */
  List<EventSummaryView> summaries();

  /**
   * Stores the published event — its status, its join code and its next version — only if the row is still the
   * draft the use case read (US-11 D4).
   */
  Write publish(Event event, UUID actor, Instant at);
}
