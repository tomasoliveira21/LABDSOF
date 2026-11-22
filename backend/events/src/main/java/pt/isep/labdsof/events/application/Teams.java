package pt.isep.labdsof.events.application;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import pt.isep.labdsof.events.domain.Team;

/** Port: the teams of this service's own database. Always keyed by event too (ADR-003 decision 3). */
public interface Teams {

  /**
   * What a write did. Every write carries the version it read and only changes the row that still holds it, so two
   * requests that start from the same read cannot both store a version (US-16 D3): {@code GONE} means the row was
   * deleted in between, and {@code CONFLICT} that it is still live but has moved on. Neither writes anything, and
   * neither publishes a TeamChanged.
   */
  enum Write { STORED, NAME_TAKEN, GONE, CONFLICT }

  /** The teams that are not deleted, with their permissions, ordered by name ignoring case. */
  List<Team> liveOfEvent(UUID eventId);

  /** One team of that event that is not deleted. */
  Optional<Team> liveOfEvent(UUID eventId, UUID teamId);

  /**
   * Stores a new team. {@code NAME_TAKEN} when another live team of the event already has the name: the partial
   * unique index team_name_per_event is the only check, so the race between two creations is decided here (D3).
   */
  Write add(Team team, UUID actor, Instant at);

  /** Stores the next version of a team (rename and categories), only if it still holds the version read. */
  Write replace(Team team, UUID actor, Instant at);

  /**
   * Soft deletes the team, keeping its permission rows (D5), only if it still holds the version read. The name it
   * held becomes free, so this never meets the unique index.
   */
  Write delete(Team team, UUID actor, Instant at);
}
