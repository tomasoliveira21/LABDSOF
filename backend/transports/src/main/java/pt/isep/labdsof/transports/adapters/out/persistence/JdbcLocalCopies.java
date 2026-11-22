package pt.isep.labdsof.transports.adapters.out.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import pt.isep.labdsof.transports.application.LocalCopies;
import pt.isep.labdsof.transports.application.ProcessedMessages;
import pt.isep.labdsof.transports.domain.EventCopy;
import pt.isep.labdsof.transports.domain.EventStatus;
import pt.isep.labdsof.transports.domain.Membership;
import pt.isep.labdsof.transports.domain.Permission;
import pt.isep.labdsof.transports.domain.Team;

/** The copies of memberships, events and teams, each upserted only by a newer version (spec D3), and the processed messageIds. */
@Repository
class JdbcLocalCopies implements LocalCopies, ProcessedMessages {

  private static final String EVENT_ID = "eventId";
  private static final String VERSION = "version";

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
        .param(EVENT_ID, eventId)
        .param("userId", userId)
        .query(JdbcLocalCopies::membership)
        .optional();
  }

  @Override
  public Optional<EventCopy> event(UUID eventId) {
    return jdbc.sql("SELECT event_id, status, version FROM event_copy WHERE event_id = :eventId")
        .param(EVENT_ID, eventId)
        .query((rs, row) -> new EventCopy(rs.getObject("event_id", UUID.class),
            EventStatus.valueOf(rs.getString("status")), rs.getLong(VERSION)))
        .optional();
  }

  @Override
  public Optional<Team> team(UUID teamId) {
    return jdbc.sql("SELECT team_id, event_id, permissions, deleted, version FROM team_copy WHERE team_id = :teamId")
        .param("teamId", teamId)
        .query(JdbcLocalCopies::team)
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
        .param(EVENT_ID, m.eventId())
        .param("userId", m.userId())
        .param("role", m.role().name())
        .param("status", m.status().name())
        .param("teamIds", m.teamIds().toArray(UUID[]::new))
        .param(VERSION, m.version())
        .update() == 1;
  }

  @Override
  public boolean upsertIfNewer(EventCopy e) {
    return jdbc.sql("""
        INSERT INTO event_copy (event_id, status, version) VALUES (:eventId, :status, :version)
        ON CONFLICT (event_id) DO UPDATE SET status = excluded.status, version = excluded.version
          WHERE event_copy.version < excluded.version
        """)
        .param(EVENT_ID, e.eventId())
        .param("status", e.status().name())
        .param(VERSION, e.version())
        .update() == 1;
  }

  /** A team never moves to another event (#214 D7): a row of another event is left as it is. */
  @Override
  public boolean upsertIfNewer(Team t) {
    return jdbc.sql("""
        INSERT INTO team_copy (team_id, event_id, permissions, deleted, version)
        VALUES (:teamId, :eventId, :permissions, :deleted, :version)
        ON CONFLICT (team_id) DO UPDATE
          SET permissions = excluded.permissions, deleted = excluded.deleted, version = excluded.version
          WHERE team_copy.version < excluded.version AND team_copy.event_id = excluded.event_id
        """)
        .param("teamId", t.teamId())
        .param(EVENT_ID, t.eventId())
        .param("permissions", t.permissions().stream().map(Permission::wireName).sorted().toArray(String[]::new))
        .param("deleted", t.deleted())
        .param(VERSION, t.version())
        .update() == 1;
  }

  private static Membership membership(ResultSet rs, int row) throws SQLException {
    return new Membership(
        rs.getObject("event_id", UUID.class),
        rs.getObject("user_id", UUID.class),
        Membership.Role.valueOf(rs.getString("role")),
        Membership.Status.valueOf(rs.getString("status")),
        Set.of((UUID[]) rs.getArray("team_ids").getArray()),
        rs.getLong(VERSION));
  }

  private static Team team(ResultSet rs, int row) throws SQLException {
    Set<Permission> permissions = Arrays.stream((String[]) rs.getArray("permissions").getArray())
        .map(Permission::fromWireName).collect(Collectors.toUnmodifiableSet());
    return new Team(rs.getObject("team_id", UUID.class), rs.getObject("event_id", UUID.class), permissions,
        rs.getBoolean("deleted"), rs.getLong(VERSION));
  }
}
