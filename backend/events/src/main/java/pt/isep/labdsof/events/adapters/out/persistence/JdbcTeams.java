package pt.isep.labdsof.events.adapters.out.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import pt.isep.labdsof.events.application.Teams;
import pt.isep.labdsof.events.domain.HelpCategory;
import pt.isep.labdsof.events.domain.Permission;
import pt.isep.labdsof.events.domain.ReportType;
import pt.isep.labdsof.events.domain.Team;
import pt.isep.labdsof.events.domain.TeamPermission;

/**
 * The team and team_permission tables (spec #192 D4). A team is read with its permissions in one query; the
 * partial unique index team_name_per_event decides a repeated name, here turned into {@link Write#NAME_TAKEN}
 * (US-16 D3), and a write that finds no live row any more into {@link Write#GONE}.
 */
@Repository
class JdbcTeams implements Teams {

  /** One row of the join: the team repeated once per permission, or once with none (LEFT JOIN). */
  private record Row(UUID teamId, UUID eventId, String name, Set<HelpCategory> categories, long version,
      String permission, String[] reportTypes) {}

  private static final String EVENT_ID = "eventId";
  private static final String TEAM_ID = "teamId";
  private static final String NAME = "name";
  private static final String CATEGORIES = "categories";
  private static final String VERSION = "version";
  private static final String ACTOR = "actor";
  private static final String AT = "at";

  private static final String SELECT = """
      SELECT t.team_id, t.event_id, t.name, t.handled_categories, t.version, p.permission, p.report_types
      FROM team t
      LEFT JOIN team_permission p ON p.event_id = t.event_id AND p.team_id = t.team_id
      WHERE t.event_id = :eventId AND t.deleted_at IS NULL
      """;

  private static final String ORDER = " ORDER BY lower(t.name), t.team_id, p.permission";

  private final JdbcClient jdbc;

  JdbcTeams(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public List<Team> liveOfEvent(UUID eventId) {
    return teamsOf(jdbc.sql(SELECT + ORDER).param(EVENT_ID, eventId).query(JdbcTeams::row).list());
  }

  @Override
  public Optional<Team> liveOfEvent(UUID eventId, UUID teamId) {
    var rows = jdbc.sql(SELECT + " AND t.team_id = :teamId" + ORDER)
        .param(EVENT_ID, eventId)
        .param(TEAM_ID, teamId)
        .query(JdbcTeams::row)
        .list();
    return teamsOf(rows).stream().findFirst();
  }

  @Override
  public Write add(Team team, UUID actor, Instant at) {
    try {
      jdbc.sql("""
          INSERT INTO team (team_id, event_id, name, handled_categories, version, created_at, created_by,
                            updated_at, updated_by)
          VALUES (:teamId, :eventId, :name, :categories, :version, :at, :actor, :at, :actor)
          """)
          .param(TEAM_ID, team.teamId())
          .param(EVENT_ID, team.eventId())
          .param(NAME, team.name())
          .param(CATEGORIES, codes(team))
          .param(VERSION, team.version())
          .param(AT, Timestamp.from(at))
          .param(ACTOR, actor)
          .update();
      return Write.STORED;
    } catch (DuplicateKeyException e) {
      return Write.NAME_TAKEN;
    }
  }

  @Override
  public Write replace(Team team, UUID actor, Instant at) {
    try {
      return jdbc.sql("""
          UPDATE team SET name = :name, handled_categories = :categories, version = :version, updated_at = :at,
                          updated_by = :actor
          WHERE event_id = :eventId AND team_id = :teamId AND deleted_at IS NULL
          """)
          .param(NAME, team.name())
          .param(CATEGORIES, codes(team))
          .param(VERSION, team.version())
          .param(AT, Timestamp.from(at))
          .param(ACTOR, actor)
          .param(EVENT_ID, team.eventId())
          .param(TEAM_ID, team.teamId())
          .update() == 1 ? Write.STORED : Write.GONE;
    } catch (DuplicateKeyException e) {
      return Write.NAME_TAKEN;
    }
  }

  @Override
  public Write delete(Team team, UUID actor, Instant at) {
    return jdbc.sql("""
        UPDATE team SET deleted_at = :at, version = :version, updated_at = :at, updated_by = :actor
        WHERE event_id = :eventId AND team_id = :teamId AND deleted_at IS NULL
        """)
        .param(AT, Timestamp.from(at))
        .param(VERSION, team.version())
        .param(ACTOR, actor)
        .param(EVENT_ID, team.eventId())
        .param(TEAM_ID, team.teamId())
        .update() == 1 ? Write.STORED : Write.GONE;
  }

  @Override
  public Write replacePermissions(Team team, UUID actor, Instant at) {
    var stored = jdbc.sql("""
        UPDATE team SET version = :version, updated_at = :at, updated_by = :actor
        WHERE event_id = :eventId AND team_id = :teamId AND deleted_at IS NULL
        """)
        .param(VERSION, team.version())
        .param(AT, Timestamp.from(at))
        .param(ACTOR, actor)
        .param(EVENT_ID, team.eventId())
        .param(TEAM_ID, team.teamId())
        .update();
    if (stored != 1) {
      return Write.GONE;
    }
    jdbc.sql("DELETE FROM team_permission WHERE event_id = :eventId AND team_id = :teamId")
        .param(EVENT_ID, team.eventId())
        .param(TEAM_ID, team.teamId())
        .update();
    for (var permission : team.permissions()) {
      jdbc.sql("""
          INSERT INTO team_permission (event_id, team_id, permission, report_types)
          VALUES (:eventId, :teamId, :permission, :reportTypes)
          """)
          .param(EVENT_ID, team.eventId())
          .param(TEAM_ID, team.teamId())
          .param("permission", permission.permission().code())
          .param("reportTypes", permission.reportTypes().stream().map(ReportType::name).toArray(String[]::new))
          .update();
    }
    return Write.STORED;
  }

  private static String[] codes(Team team) {
    return team.handledCategories().stream().map(HelpCategory::name).toArray(String[]::new);
  }

  private static Row row(ResultSet rs, int number) throws SQLException {
    var categories = EnumSet.noneOf(HelpCategory.class);
    for (var code : (String[]) rs.getArray("handled_categories").getArray()) {
      categories.add(HelpCategory.valueOf(code));
    }
    var permission = rs.getString("permission");
    var types = rs.getArray("report_types");
    return new Row(rs.getObject("team_id", UUID.class), rs.getObject("event_id", UUID.class), rs.getString(NAME),
        categories, rs.getLong(VERSION), permission, types == null ? new String[0] : (String[]) types.getArray());
  }

  /** Folds the join back into one team per id, keeping the query's order. */
  private static List<Team> teamsOf(List<Row> rows) {
    var permissions = new LinkedHashMap<UUID, List<TeamPermission>>();
    var first = new LinkedHashMap<UUID, Row>();
    for (var row : rows) {
      first.putIfAbsent(row.teamId(), row);
      var found = permissions.computeIfAbsent(row.teamId(), id -> new ArrayList<>());
      if (row.permission() != null) {
        found.add(new TeamPermission(Permission.ofCode(row.permission()), reportTypes(row.reportTypes())));
      }
    }
    return first.values().stream().map(row -> new Team(row.teamId(), row.eventId(), row.name(),
        permissions.get(row.teamId()), row.categories(), false, row.version())).toList();
  }

  private static Set<ReportType> reportTypes(String[] codes) {
    var types = EnumSet.noneOf(ReportType.class);
    Arrays.stream(codes).map(ReportType::valueOf).forEach(types::add);
    return types;
  }
}
