package pt.isep.labdsof.events.adapters.out.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import pt.isep.labdsof.events.application.LocalCopies;
import pt.isep.labdsof.events.application.ProcessedMessages;
import pt.isep.labdsof.events.domain.Membership;

/** The membership copy, upserted by version (spec D2), and the processed messageIds. */
@Repository
class JdbcLocalCopies implements LocalCopies, ProcessedMessages {

  private final JdbcClient jdbc;

  JdbcLocalCopies(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public boolean markProcessed(UUID messageId) {
    return jdbc.sql("INSERT INTO processed_message (message_id) VALUES (:id) ON CONFLICT DO NOTHING")
        .param("id", messageId)
        .update() == 1;
  }

  @Override
  public Optional<Membership> membership(UUID eventId, UUID userId) {
    return jdbc.sql("""
        SELECT event_id, user_id, role, status, team_ids, version
        FROM membership_copy WHERE event_id = :eventId AND user_id = :userId
        """)
        .param("eventId", eventId)
        .param("userId", userId)
        .query(JdbcLocalCopies::membership)
        .optional();
  }

  @Override
  public boolean upsertIfNewer(Membership m) {
    return jdbc.sql("""
        INSERT INTO membership_copy (event_id, user_id, role, status, team_ids, version)
        VALUES (:eventId, :userId, :role, :status, :teamIds, :version)
        ON CONFLICT (event_id, user_id) DO UPDATE
          SET role = excluded.role, status = excluded.status, team_ids = excluded.team_ids,
              version = excluded.version
          WHERE membership_copy.version < excluded.version
        """)
        .param("eventId", m.eventId())
        .param("userId", m.userId())
        .param("role", m.role().name())
        .param("status", m.status().name())
        .param("teamIds", m.teamIds().toArray(UUID[]::new))
        .param("version", m.version())
        .update() == 1;
  }

  private static Membership membership(ResultSet rs, int row) throws SQLException {
    return new Membership(
        rs.getObject("event_id", UUID.class),
        rs.getObject("user_id", UUID.class),
        Membership.Role.valueOf(rs.getString("role")),
        Membership.Status.valueOf(rs.getString("status")),
        Set.of((UUID[]) rs.getArray("team_ids").getArray()),
        rs.getLong("version"));
  }
}
