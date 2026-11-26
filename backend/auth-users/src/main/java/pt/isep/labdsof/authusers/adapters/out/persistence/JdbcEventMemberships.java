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

  /**
   * The assignment {@code t} is to a team that is still <em>live</em> in the copy (D4). Events deletes a team by
   * flipping {@code deleted} in its {@code TeamChanged}, and the assignment row survives that — nothing tells this
   * service to delete it. Without this, the next {@code MembershipChanged} for the member would carry a team that
   * no longer exists, against what {@code membership.changed.schema.json} promises its three consumers, and the
   * back office's picker would still show it (review of #255). Every read of a membership's teams uses it.
   */
  private static final String LIVE_TEAM = """
      AND EXISTS (SELECT 1 FROM event_team_copy c
                  WHERE c.team_id = t.team_id AND c.event_id = t.event_id AND c.payload ->> 'deleted' = 'false')
      """;

  private static final String TEAM_IDS = """
      ARRAY(SELECT t.team_id FROM team_assignment t
            WHERE t.event_id = m.event_id AND t.user_id = m.user_id
      """ + LIVE_TEAM + """
            ORDER BY t.team_id) AS team_ids
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
  public boolean insertIfAbsent(EventMembership membership, UUID actorUserId, Instant at) {
    // ON CONFLICT DO NOTHING, not a lock: the loser of two joins at once reads the winner's row (US-12 D5)
    return jdbc.sql("""
        INSERT INTO event_membership (event_id, user_id, role, version, created_at, created_by, updated_at,
                                      updated_by)
        VALUES (:event, :user, :role, :version, :at, :actor, :at, :actor)
        ON CONFLICT (event_id, user_id) DO NOTHING
        """)
        .param("event", membership.eventId()).param("user", membership.userId())
        .param("role", membership.role().name()).param("version", membership.version())
        .param("at", Timestamp.from(at)).param("actor", actorUserId)
        .update() == 1;
  }

  /**
   * The first EventAdmin of a new event, or an existing member promoted (US-06). One statement for both, so the
   * two cases cannot drift: the role and the version are the use case's, and {@code team_assignment} is never
   * touched, so a promoted member keeps the teams they were in.
   */
  @Override
  public void upsertRole(EventMembership updated, UUID actorUserId, Instant at) {
    jdbc.sql("""
        INSERT INTO event_membership (event_id, user_id, role, version, created_at, created_by, updated_at,
                                      updated_by)
        VALUES (:event, :user, :role, :version, :at, :actor, :at, :actor)
        ON CONFLICT (event_id, user_id) DO UPDATE
          SET role = excluded.role, version = excluded.version, updated_at = excluded.updated_at,
              updated_by = excluded.updated_by
        """)
        .param("event", updated.eventId()).param("user", updated.userId())
        .param("role", updated.role().name()).param("version", updated.version())
        .param("at", Timestamp.from(at)).param("actor", actorUserId)
        .update();
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

  @Override
  public void clearAssignment(UUID eventId, UUID userId, UUID teamId) {
    jdbc.sql("DELETE FROM team_assignment WHERE event_id = :event AND user_id = :user AND team_id = :team")
        .param("event", eventId).param("user", userId).param("team", teamId)
        .update();
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
          -- The filter reads the teams by the same rule as TEAM_IDS, or a deleted team would still match the
          -- members it used to have while the list showed them with no team at all
          AND (CAST(:team AS uuid) IS NULL OR EXISTS (SELECT 1 FROM team_assignment t
                                        WHERE t.event_id = m.event_id AND t.user_id = m.user_id
                                          AND t.team_id = CAST(:team AS uuid)
        """ + LIVE_TEAM + """
                                        ))
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
