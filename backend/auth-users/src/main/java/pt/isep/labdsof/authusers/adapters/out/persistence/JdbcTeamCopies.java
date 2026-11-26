package pt.isep.labdsof.authusers.adapters.out.persistence;

import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import pt.isep.labdsof.authusers.application.TeamCopies;

/**
 * {@code event_team_copy} (V2, #241), where the {@code TeamChanged} consumer keeps each team's latest payload as
 * Events published it. A deleted team stays in the copy with {@code deleted: true} and a higher version (US-16),
 * so the check of US-18 D4 reads the flag instead of the row's absence.
 */
@Repository
class JdbcTeamCopies implements TeamCopies {

  private final JdbcClient jdbc;

  JdbcTeamCopies(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public boolean isLiveTeamOf(UUID eventId, UUID teamId) {
    return jdbc.sql("""
        SELECT 1 FROM event_team_copy
        WHERE team_id = :team AND event_id = :event AND payload ->> 'deleted' = 'false'
        """)
        .param("team", teamId).param("event", eventId)
        .query(Integer.class).optional().isPresent();
  }
}
