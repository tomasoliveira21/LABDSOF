package pt.isep.labdsof.authusers.adapters.out.persistence;

import java.sql.Array;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import pt.isep.labdsof.authusers.application.EventMemberships;
import pt.isep.labdsof.authusers.domain.EventMembership;
import pt.isep.labdsof.authusers.domain.EventRole;

/**
 * {@code event_membership} and {@code team_assignment} (V1), with the team ids aggregated as
 * {@code JdbcMyMemberships} does. The lock of US-18 D7 is on the membership's row, which no other write of this
 * service takes.
 */
@Repository
class JdbcEventMemberships implements EventMemberships {

  private static final String TEAM_IDS = """
      ARRAY(SELECT t.team_id FROM team_assignment t
            WHERE t.event_id = m.event_id AND t.user_id = m.user_id ORDER BY t.team_id) AS team_ids
      """;

  private final JdbcClient jdbc;

  JdbcEventMemberships(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Optional<EventMembership> find(UUID eventId, UUID userId) {
    return jdbc.sql("SELECT m.event_id, m.user_id, m.role, m.version, " + TEAM_IDS + """
        FROM event_membership m
        WHERE m.event_id = :event AND m.user_id = :user
        """)
        .param("event", eventId).param("user", userId)
        .query((row, number) -> new EventMembership(row.getObject("event_id", UUID.class),
            row.getObject("user_id", UUID.class), EventRole.valueOf(row.getString("role")),
            teamIds(row.getArray("team_ids")), row.getLong("version")))
        .optional();
  }

  @Override
  public Optional<EventMembership> lockForUpdate(UUID eventId, UUID userId) {
    // An aggregate subquery cannot be locked, so the row is taken first and the teams are read right after it
    var locked = jdbc.sql("SELECT 1 FROM event_membership WHERE event_id = :event AND user_id = :user FOR UPDATE")
        .param("event", eventId).param("user", userId)
        .query(Integer.class).optional();
    return locked.isEmpty() ? Optional.empty() : find(eventId, userId);
  }

  @Override
  public void addToTeam(EventMembership updated, UUID teamId, UUID actorUserId, Instant at) {
    jdbc.sql("""
        INSERT INTO team_assignment (event_id, user_id, team_id, assigned_at, assigned_by)
        VALUES (:event, :user, :team, :at, :actor)
        """)
        .param("event", updated.eventId()).param("user", updated.userId()).param("team", teamId)
        .param("at", Timestamp.from(at)).param("actor", actorUserId)
        .update();
    version(updated, actorUserId, at);
  }

  @Override
  public void removeFromTeam(EventMembership updated, UUID teamId, UUID actorUserId, Instant at) {
    jdbc.sql("DELETE FROM team_assignment WHERE event_id = :event AND user_id = :user AND team_id = :team")
        .param("event", updated.eventId()).param("user", updated.userId()).param("team", teamId)
        .update();
    version(updated, actorUserId, at);
  }

  /** The version the membership now carries, with who changed it and when (D6, D8). */
  private void version(EventMembership updated, UUID actorUserId, Instant at) {
    jdbc.sql("""
        UPDATE event_membership SET version = :version, updated_at = :at, updated_by = :actor
        WHERE event_id = :event AND user_id = :user
        """)
        .param("version", updated.version()).param("at", Timestamp.from(at)).param("actor", actorUserId)
        .param("event", updated.eventId()).param("user", updated.userId())
        .update();
  }

  @Override
  public List<Member> list(UUID eventId, EventRole role, UUID teamId) {
    return jdbc.sql("SELECT m.user_id, a.display_name, m.role, " + TEAM_IDS + """
        FROM event_membership m
        JOIN user_account a ON a.user_id = m.user_id
        WHERE m.event_id = :event
          -- The casts are what lets a filter be left out: PostgreSQL cannot infer the type of a null parameter
          AND (CAST(:role AS text) IS NULL OR m.role = CAST(:role AS text))
          AND (CAST(:team AS uuid) IS NULL OR EXISTS (SELECT 1 FROM team_assignment f
                                        WHERE f.event_id = m.event_id AND f.user_id = m.user_id
                                          AND f.team_id = CAST(:team AS uuid)))
        ORDER BY lower(a.display_name), m.user_id
        """)
        .param("event", eventId)
        .param("role", role == null ? null : role.name())
        .param("team", teamId)
        .query((row, number) -> new Member(row.getObject("user_id", UUID.class), row.getString("display_name"),
            EventRole.valueOf(row.getString("role")), teamIds(row.getArray("team_ids"))))
        .list();
  }

  /** The array is freed here, as every read of it in this service does: a cursor's arrays are not garbage. */
  private static List<UUID> teamIds(Array array) throws SQLException {
    try {
      return List.of((UUID[]) array.getArray());
    } finally {
      array.free();
    }
  }
}
