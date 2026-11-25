package pt.isep.labdsof.reports.adapters.out.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import pt.isep.labdsof.reports.application.LocalCopies;
import pt.isep.labdsof.reports.application.ProcessedMessages;
import pt.isep.labdsof.reports.domain.Category;
import pt.isep.labdsof.reports.domain.Membership;
import pt.isep.labdsof.reports.domain.Permission;
import pt.isep.labdsof.reports.domain.Team;
import pt.isep.labdsof.reports.domain.Team.TeamPermission;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Local copies, upserted by version (spec D7), and the processed messageIds. */
@Repository
class JdbcLocalCopies implements LocalCopies, ProcessedMessages {

  /** How a team's permissions are stored: the TeamChanged shape, `[{ permission, reportTypes }]`. */
  record StoredPermission(String permission, List<String> reportTypes) {}

  private static final Logger log = LoggerFactory.getLogger(JdbcLocalCopies.class);
  private static final String EVENT_ID = "eventId";
  private static final String VERSION = "version";

  private final JdbcClient jdbc;
  private final JsonMapper json;
  private final Clock clock;

  JdbcLocalCopies(JdbcClient jdbc, JsonMapper json, Clock clock) {
    this.jdbc = jdbc;
    this.json = json;
    this.clock = clock;
  }

  @Override
  public boolean markProcessed(UUID messageId) {
    return jdbc.sql("INSERT INTO processed_message (message_id) VALUES (:id) ON CONFLICT DO NOTHING")
        .param("id", messageId)
        .update() == 1;
  }

  @Override
  public Optional<Membership> membership(UUID eventId, String userId) {
    return jdbc.sql("SELECT * FROM membership_copy WHERE event_id = :eventId AND user_id = :userId")
        .param(EVENT_ID, eventId)
        .param("userId", userId)
        .query(JdbcLocalCopies::membership)
        .optional();
  }

  @Override
  public List<Team> teams(UUID eventId, Collection<UUID> teamIds) {
    if (teamIds.isEmpty()) {
      return List.of();
    }
    return jdbc.sql("SELECT * FROM team_copy WHERE event_id = :eventId AND team_id IN (:teamIds)")
        .param(EVENT_ID, eventId)
        .param("teamIds", teamIds)
        .query(this::team)
        .list();
  }

  @Override
  public boolean teamsChangedSince(UUID eventId, Instant since) {
    return jdbc.sql("SELECT EXISTS (SELECT 1 FROM team_copy WHERE event_id = :eventId AND changed_at > :since)")
        .param(EVENT_ID, eventId).param("since", Timestamp.from(since)).query(Boolean.class).single();
  }

  @Override
  public List<Team> teams(UUID eventId) {
    return jdbc.sql("SELECT * FROM team_copy WHERE event_id = :eventId").param(EVENT_ID, eventId).query(this::team)
        .list();
  }

  @Override
  public Optional<Team> teamForShare(UUID eventId, UUID teamId) {
    return jdbc.sql("SELECT * FROM team_copy WHERE event_id = :eventId AND team_id = :teamId FOR SHARE")
        .param(EVENT_ID, eventId)
        .param("teamId", teamId)
        .query(this::team)
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
  public boolean upsertIfNewer(Team t) {
    var permissions = t.permissions().stream()
        .map(p -> new StoredPermission(p.permission().wireName(), p.reportTypes().stream().sorted().toList()))
        .toList();
    // A team never moves to another event: an update for another event_id changes nothing.
    var applied = jdbc.sql("""
        INSERT INTO team_copy (team_id, event_id, name, permissions, handled_categories, deleted, version,
            changed_at)
        VALUES (:teamId, :eventId, :name, CAST(:permissions AS jsonb), :categories, :deleted, :version, :changedAt)
        ON CONFLICT (team_id) DO UPDATE
          SET name = excluded.name, permissions = excluded.permissions,
              handled_categories = excluded.handled_categories, deleted = excluded.deleted,
              version = excluded.version, changed_at = excluded.changed_at
          WHERE team_copy.version < excluded.version AND team_copy.event_id = excluded.event_id
        """)
        .param("teamId", t.teamId())
        .param(EVENT_ID, t.eventId())
        .param("name", t.name())
        .param("permissions", json.writeValueAsString(permissions))
        .param("categories", t.handledCategories().stream().map(Enum::name).sorted().toArray(String[]::new))
        .param("deleted", t.deleted())
        .param(VERSION, t.version())
        // The application's clock, as help_request.updated_at, so a poll's updatedSince compares the same clock
        .param("changedAt", Timestamp.from(clock.instant().truncatedTo(ChronoUnit.MICROS)))
        .update() == 1;
    if (!applied) {
      var savedEvent = jdbc.sql("SELECT event_id FROM team_copy WHERE team_id = :teamId")
          .param("teamId", t.teamId()).query(UUID.class).single();
      if (!savedEvent.equals(t.eventId())) { // not stale: Events broke an invariant
        log.warn("TeamChanged for team {} of event {} ignored: the team belongs to event {}", t.teamId(),
            t.eventId(), savedEvent);
      }
    }
    return applied;
  }

  private static Membership membership(ResultSet rs, int row) throws SQLException {
    return new Membership(
        rs.getObject("event_id", UUID.class),
        rs.getString("user_id"),
        Membership.Role.valueOf(rs.getString("role")),
        Membership.Status.valueOf(rs.getString("status")),
        Set.of((UUID[]) rs.getArray("team_ids").getArray()),
        rs.getLong(VERSION));
  }

  private Team team(ResultSet rs, int row) throws SQLException {
    List<StoredPermission> stored = json.readValue(rs.getString("permissions"), new TypeReference<>() {});
    return new Team(
        rs.getObject("team_id", UUID.class),
        rs.getObject("event_id", UUID.class),
        rs.getString("name"),
        stored.stream().map(p -> new TeamPermission(Permission.fromWireName(p.permission()),
            Set.copyOf(p.reportTypes()))).toList(),
        Arrays.stream((String[]) rs.getArray("handled_categories").getArray()).map(Category::valueOf)
            .collect(Collectors.toSet()),
        rs.getBoolean("deleted"),
        rs.getLong(VERSION));
  }
}
