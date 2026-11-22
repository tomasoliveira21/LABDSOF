package pt.isep.labdsof.reports.adapters.out.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import pt.isep.labdsof.reports.application.HelpRequests;
import pt.isep.labdsof.reports.domain.Actor;
import pt.isep.labdsof.reports.domain.Category;
import pt.isep.labdsof.reports.domain.HelpRequest;
import pt.isep.labdsof.reports.domain.HelpRequestChange;
import pt.isep.labdsof.reports.domain.HelpRequestStatus;
import pt.isep.labdsof.reports.domain.HistoryEntry;
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

  private static final String ARRIVAL_ORDER = " ORDER BY r.submitted_at, r.id";
  private static final String EVENT_ID = "eventId";
  private static final String CATEGORY = "category";
  private static final String PRIORITY = "priority";

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
        .param(EVENT_ID, r.eventId())
        .param("requesterId", r.requesterId())
        .param("clientRequestId", r.clientRequestId())
        .param("description", r.description())
        .param("mapVersion", r.location().mapVersion())
        .param("x", r.location().x())
        .param("y", r.location().y())
        .param(CATEGORY, name(r.category()))
        .param(PRIORITY, name(r.priority()))
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
        .param(EVENT_ID, r.eventId())
        .param("state", t.state().name())
        .param(CATEGORY, name(t.suggestedCategory()))
        .param(PRIORITY, name(t.suggestedPriority()))
        .param("reason", t.suggestedReason())
        .param("notTriagedReason", name(t.notTriagedReason()))
        .param("updatedAt", Timestamp.from(r.updatedAt()))
        .update();
    return true;
  }

  @Override
  public Optional<HelpRequest> find(UUID eventId, UUID requestId) {
    return jdbc.sql(SELECT + " WHERE r.event_id = :eventId AND r.id = :id")
        .param(EVENT_ID, eventId)
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
        .param(EVENT_ID, eventId)
        .param("requesterId", requesterId)
        .param("clientRequestId", clientRequestId)
        .query(JdbcHelpRequests::map)
        .optional();
  }

  @Override
  public List<HelpRequest> open(UUID eventId) {
    return jdbc.sql(SELECT + " WHERE r.event_id = :eventId AND r.status <> 'Closed'" + ARRIVAL_ORDER)
        .param(EVENT_ID, eventId)
        .query(JdbcHelpRequests::map)
        .list();
  }

  @Override
  public List<HelpRequest> candidatesSince(UUID eventId, Instant since) {
    // Truncated down to the database's microsecond, so rounding can only widen the superset
    var from = since.truncatedTo(ChronoUnit.MICROS);
    return jdbc.sql(SELECT + """
         WHERE r.event_id = :eventId AND (r.updated_at > :since OR r.submitted_at > :submittedAfter)
        """ + ARRIVAL_ORDER)
        .param(EVENT_ID, eventId)
        .param("since", Timestamp.from(from))
        .param("submittedAfter", Timestamp.from(from.minus(TriageSuggestion.TRIAGE_DEADLINE)))
        .query(JdbcHelpRequests::map)
        .list();
  }

  @Override
  public boolean saveTriage(HelpRequest r) {
    // One statement: the triage is written only while it still awaits the model, and only then does the request's
    // updated_at move, so the live list shows it (spec US-34 D6, D8; US-26 D5)
    var t = r.triage();
    return jdbc.sql("""
        WITH saved AS (
            UPDATE triage_suggestion SET state = :state, suggested_category = :category,
                suggested_priority = :priority, suggested_reason = :reason, not_triaged_reason = :notTriagedReason,
                updated_at = :updatedAt
            WHERE request_id = :id AND event_id = :eventId AND state = 'Pending' AND not_triaged_reason IS NULL
              AND clock_timestamp() <= :deadline
            RETURNING request_id)
        UPDATE help_request SET updated_at = :updatedAt WHERE id IN (SELECT request_id FROM saved)
        """)
        .param("id", r.id())
        .param(EVENT_ID, r.eventId())
        .param("state", t.state().name())
        .param(CATEGORY, name(t.suggestedCategory()))
        .param(PRIORITY, name(t.suggestedPriority()))
        .param("reason", t.suggestedReason())
        .param("notTriagedReason", name(t.notTriagedReason()))
        .param("updatedAt", Timestamp.from(r.updatedAt()))
        // A result written after the deadline would turn a NotTriaged/Timeout the operator saw into a suggestion
        .param("deadline", Timestamp.from(r.submittedAt().plus(TriageSuggestion.TRIAGE_DEADLINE)))
        .update() == 1;
  }

  @Override
  public Optional<HelpRequest> findForUpdate(UUID eventId, UUID requestId) {
    // Locks both rows in the worker's order (saveTriage: triage_suggestion, then help_request), or each would wait
    // on the other: the worker's conditional UPDATE waits, then re-reads the decided triage (spec US-35 D4)
    jdbc.sql("SELECT 1 FROM triage_suggestion WHERE request_id = :id AND event_id = :eventId FOR UPDATE")
        .param(EVENT_ID, eventId)
        .param("id", requestId)
        .query()
        .listOfRows();
    return jdbc.sql(SELECT + " WHERE r.event_id = :eventId AND r.id = :id FOR UPDATE")
        .param(EVENT_ID, eventId)
        .param("id", requestId)
        .query(JdbcHelpRequests::map)
        .optional();
  }

  @Override
  public void saveDecision(HelpRequest r, Actor actor, List<HelpRequestChange> changes) {
    var at = Timestamp.from(r.updatedAt());
    int updated = jdbc.sql("""
        UPDATE help_request SET category = :category, priority = :priority, status = :status,
            updated_at = :updatedAt, version = :version
        WHERE id = :id AND event_id = :eventId AND version = :version - 1
        """)
        .param("id", r.id())
        .param(EVENT_ID, r.eventId())
        .param(CATEGORY, name(r.category()))
        .param(PRIORITY, name(r.priority()))
        .param("status", r.status().name())
        .param("updatedAt", at)
        .param("version", r.version())
        .update();
    if (updated != 1) {
      // The row is locked by findForUpdate, so this is a caller that skipped it
      throw new IllegalStateException("help request changed without its lock");
    }
    jdbc.sql("""
        UPDATE triage_suggestion SET state = :state, not_triaged_reason = :notTriagedReason, updated_at = :updatedAt
        WHERE request_id = :id AND event_id = :eventId
        """)
        .param("id", r.id())
        .param(EVENT_ID, r.eventId())
        .param("state", r.triage().state().name())
        .param("notTriagedReason", name(r.triage().notTriagedReason()))
        .param("updatedAt", at)
        .update();
    for (var change : changes) {
      jdbc.sql("""
          INSERT INTO help_request_change (request_id, event_id, actor_user_id, actor_name, changed_at, field,
              old_value, new_value)
          VALUES (:id, :eventId, :actor, :actorName, :changedAt, :field, :oldValue, :newValue)
          """)
          .param("id", r.id())
          .param(EVENT_ID, r.eventId())
          .param("actor", actor.userId())
          .param("actorName", actor.name())
          .param("changedAt", at)
          .param("field", change.field())
          .param("oldValue", change.oldValue())
          .param("newValue", change.newValue())
          .update();
    }
  }

  @Override
  public List<HistoryEntry> history(UUID eventId, UUID requestId) {
    return jdbc.sql("""
        SELECT field, old_value, new_value, actor_user_id, actor_name, changed_at FROM help_request_change
        WHERE event_id = :eventId AND request_id = :id ORDER BY id
        """)
        .param(EVENT_ID, eventId)
        .param("id", requestId)
        .query((rs, row) -> new HistoryEntry(rs.getString("field"), rs.getString("old_value"),
            rs.getString("new_value"), rs.getString("actor_user_id"), rs.getString("actor_name"),
            rs.getTimestamp("changed_at").toInstant()))
        .list();
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
        value(Category.class, rs.getString(CATEGORY)),
        value(Priority.class, rs.getString(PRIORITY)),
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
