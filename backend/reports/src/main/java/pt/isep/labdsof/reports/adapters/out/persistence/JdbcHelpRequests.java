package pt.isep.labdsof.reports.adapters.out.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import pt.isep.labdsof.reports.application.HelpRequests;
import pt.isep.labdsof.reports.domain.Category;
import pt.isep.labdsof.reports.domain.HelpRequest;
import pt.isep.labdsof.reports.domain.HelpRequestStatus;
import pt.isep.labdsof.reports.domain.Location;
import pt.isep.labdsof.reports.domain.NotTriagedReason;
import pt.isep.labdsof.reports.domain.Priority;
import pt.isep.labdsof.reports.domain.TriageState;
import pt.isep.labdsof.reports.domain.TriageSuggestion;

@Repository
class JdbcHelpRequests implements HelpRequests {

  private static final String SELECT = """
      SELECT r.*, t.state, t.suggested_category, t.suggested_priority, t.suggested_reason,
             t.not_triaged_reason
      FROM help_request r JOIN triage_suggestion t ON t.request_id = r.id
      """;

  private final JdbcClient jdbc;

  JdbcHelpRequests(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public boolean insertIfNew(HelpRequest r) {
    // ON CONFLICT waits for a concurrent insert of the same clientRequestId to commit, then does nothing.
    int inserted = jdbc.sql("""
        INSERT INTO help_request (id, event_id, requester_id, client_request_id, description, map_version,
            x, y, category, priority, status, default_team_id, routed_team_id, submitted_at, updated_at, version)
        VALUES (:id, :eventId, :requesterId, :clientRequestId, :description, :mapVersion, :x, :y,
            :category, :priority, :status, :defaultTeamId, :routedTeamId, :submittedAt, :updatedAt, :version)
        ON CONFLICT ON CONSTRAINT help_request_client_request DO NOTHING
        """)
        .param("id", r.id())
        .param("eventId", r.eventId())
        .param("requesterId", r.requesterId())
        .param("clientRequestId", r.clientRequestId())
        .param("description", r.description())
        .param("mapVersion", r.location().mapVersion())
        .param("x", r.location().x())
        .param("y", r.location().y())
        .param("category", name(r.category()))
        .param("priority", name(r.priority()))
        .param("status", r.status().name())
        .param("defaultTeamId", r.defaultTeamId())
        .param("routedTeamId", r.routedTeamId())
        .param("submittedAt", Timestamp.from(r.submittedAt()))
        .param("updatedAt", Timestamp.from(r.updatedAt()))
        .param("version", r.version())
        .update();
    if (inserted == 0) {
      return false;
    }
    var t = r.triage();
    jdbc.sql("""
        INSERT INTO triage_suggestion (request_id, event_id, state, suggested_category, suggested_priority,
            suggested_reason, not_triaged_reason, updated_at)
        VALUES (:id, :eventId, :state, :category, :priority, :reason, :notTriagedReason, :updatedAt)
        """)
        .param("id", r.id())
        .param("eventId", r.eventId())
        .param("state", t.state().name())
        .param("category", name(t.suggestedCategory()))
        .param("priority", name(t.suggestedPriority()))
        .param("reason", t.suggestedReason())
        .param("notTriagedReason", name(t.notTriagedReason()))
        .param("updatedAt", Timestamp.from(r.updatedAt()))
        .update();
    return true;
  }

  @Override
  public Optional<HelpRequest> find(UUID eventId, UUID requestId) {
    return jdbc.sql(SELECT + " WHERE r.event_id = :eventId AND r.id = :id")
        .param("eventId", eventId)
        .param("id", requestId)
        .query(JdbcHelpRequests::map)
        .optional();
  }

  @Override
  public Optional<HelpRequest> findByClientRequestId(UUID eventId, String requesterId,
      UUID clientRequestId) {
    return jdbc.sql(SELECT + """
         WHERE r.event_id = :eventId AND r.requester_id = :requesterId
           AND r.client_request_id = :clientRequestId
        """)
        .param("eventId", eventId)
        .param("requesterId", requesterId)
        .param("clientRequestId", clientRequestId)
        .query(JdbcHelpRequests::map)
        .optional();
  }

  private static HelpRequest map(ResultSet rs, int row) throws SQLException {
    var triage = new TriageSuggestion(
        TriageState.valueOf(rs.getString("state")),
        value(Category.class, rs.getString("suggested_category")),
        value(Priority.class, rs.getString("suggested_priority")),
        rs.getString("suggested_reason"),
        value(NotTriagedReason.class, rs.getString("not_triaged_reason")));
    return new HelpRequest(
        rs.getObject("id", UUID.class),
        rs.getObject("event_id", UUID.class),
        rs.getString("requester_id"),
        rs.getObject("client_request_id", UUID.class),
        rs.getString("description"),
        new Location(rs.getInt("map_version"), rs.getDouble("x"), rs.getDouble("y")),
        value(Category.class, rs.getString("category")),
        value(Priority.class, rs.getString("priority")),
        HelpRequestStatus.valueOf(rs.getString("status")),
        rs.getObject("default_team_id", UUID.class),
        rs.getObject("routed_team_id", UUID.class),
        triage,
        rs.getTimestamp("submitted_at").toInstant(),
        rs.getTimestamp("updated_at").toInstant(),
        rs.getLong("version"));
  }

  private static String name(Enum<?> value) {
    return value == null ? null : value.name();
  }

  private static <E extends Enum<E>> E value(Class<E> type, String name) {
    return name == null ? null : Enum.valueOf(type, name);
  }
}
