package pt.isep.labdsof.transports.application;

import java.util.Optional;
import java.util.UUID;
import pt.isep.labdsof.transports.domain.EventCopy;
import pt.isep.labdsof.transports.domain.Membership;
import pt.isep.labdsof.transports.domain.Team;

/** Port: the local copies of memberships (Auth &amp; Users), events and teams (Events), ADR-004 and spec D3. */
public interface LocalCopies {

  /** Always both keys: event and user (ADR-003 decision 3). */
  Optional<Membership> membership(UUID eventId, UUID userId);

  Optional<EventCopy> event(UUID eventId);

  Optional<Team> team(UUID teamId);

  /** False when the copy already has this or a newer version. */
  boolean upsertIfNewer(Membership membership);

  /** False when the copy already has this or a newer version. */
  boolean upsertIfNewer(EventCopy event);

  /** False when the copy already has this or a newer version, or belongs to another event. */
  boolean upsertIfNewer(Team team);
}
