package pt.isep.labdsof.events.application;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import pt.isep.labdsof.events.domain.Team;

/** Port: the teams of this service's own database. Always keyed by event too (ADR-003 decision 3). */
public interface Teams {

  /**
   * What a write did. {@code GONE} means the live row the use case had just read was not there any more, so
   * another request deleted it in between: nothing is written and nothing is published (US-16 D3).
   */
  enum Write { STORED, NAME_TAKEN, GONE }

  /** The teams that are not deleted, with their permissions, ordered by name ignoring case. */
  List<Team> liveOfEvent(UUID eventId);

  /** One team of that event that is not deleted. */
  Optional<Team> liveOfEvent(UUID eventId, UUID teamId);

  /**
   * Stores a new team. {@code NAME_TAKEN} when another live team of the event already has the name: the partial
   * unique index team_name_per_event is the only check, so the race between two creations is decided here (D3).
   */
  Write add(Team team, UUID actor, Instant at);

  /** Stores the next version of a team (rename and categories). */
  Write replace(Team team, UUID actor, Instant at);

  /**
   * Soft deletes the team, keeping its permission rows (D5). The name it held becomes free, so this never meets
   * the unique index.
   */
  Write delete(Team team, UUID actor, Instant at);

  /**
   * Replaces the team's permission rows by the ones the team now carries and stores its next version, in one
   * statement each (US-17 D1). Never {@code NAME_TAKEN}: the name does not change.
   */
  Write replacePermissions(Team team, UUID actor, Instant at);
}
