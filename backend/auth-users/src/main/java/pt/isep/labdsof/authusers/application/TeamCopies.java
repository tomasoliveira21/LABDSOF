package pt.isep.labdsof.authusers.application;

import java.util.UUID;

/**
 * Port: the teams this service knows about, which are the copies the {@code TeamChanged} consumer of #241 keeps
 * (API outline §7.3, ADR-006). Events owns them; nothing here creates or changes a team.
 */
public interface TeamCopies {

  /** True when the copy holds this team, it belongs to this event and Events has not deleted it (spec D4). */
  boolean isLiveTeamOf(UUID eventId, UUID teamId);
}
