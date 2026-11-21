package pt.isep.labdsof.events.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import pt.isep.labdsof.events.domain.TeamPermission;

/**
 * Port. US-17 AC-4, spec D6: a change of a team's permissions is recorded with who made it and when. The team
 * row keeps {@code updated_by} and {@code updated_at}, TeamChanged carries the new set, and this writes the line
 * that makes it visible in the logs — with the pseudonymous {@code userId} only, never a name or an e-mail
 * (M-10).
 */
public interface TeamAudit {

  void permissionsReplaced(UUID userId, UUID eventId, UUID teamId, Instant at,
      List<TeamPermission> permissions);
}
