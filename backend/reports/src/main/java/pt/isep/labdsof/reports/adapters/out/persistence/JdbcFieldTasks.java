package pt.isep.labdsof.reports.adapters.out.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import pt.isep.labdsof.reports.application.FieldTasks;
import pt.isep.labdsof.reports.domain.FieldTask;

/**
 * The field tasks (spec US-29 D4) and a team's list of them (US-31 D2). Every statement is filtered by event (ADR-003
 * decision 3).
 */
@Repository
class JdbcFieldTasks implements FieldTasks {

  private static final String EVENT_ID = "eventId";

  private final JdbcClient jdbc;

  JdbcFieldTasks(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void dispatch(UUID taskId, UUID eventId, UUID requestId, UUID teamId, Instant at) {
    jdbc.sql("""
        INSERT INTO field_task (id, event_id, help_request_id, team_id, purpose, status, dispatched_at, updated_at,
            version)
        VALUES (:id, :eventId, :requestId, :teamId, 'Assist', 'Open', :at, :at, 1)
        """)
        .param("id", taskId)
        .param(EVENT_ID, eventId)
        .param("requestId", requestId)
        .param("teamId", teamId)
        .param("at", Timestamp.from(at))
        .update();
  }

  @Override
  public void cancelLive(UUID eventId, UUID requestId, Instant at) {
    jdbc.sql("""
        UPDATE field_task SET status = 'Cancelled', updated_at = :at, version = version + 1
        WHERE event_id = :eventId AND help_request_id = :requestId AND status IN ('Open', 'Accepted')
        """)
        .param(EVENT_ID, eventId)
        .param("requestId", requestId)
        .param("at", Timestamp.from(at))
        .update();
  }

  @Override
  public List<FieldTask> live(UUID eventId, Collection<UUID> teamIds) {
    if (teamIds.isEmpty()) {
      return List.of(); // IN () is not SQL
    }
    return jdbc.sql("SELECT " + JdbcHelpRequests.COLUMNS + """
             , f.id AS task_id, f.team_id AS task_team_id, f.status AS task_status, f.accepted_by AS task_accepted_by,
               f.dispatched_at AS task_dispatched_at, f.updated_at AS task_updated_at
        FROM field_task f
        JOIN help_request r ON r.id = f.help_request_id AND r.event_id = f.event_id
        JOIN triage_suggestion t ON t.request_id = r.id
        WHERE f.event_id = :eventId AND f.team_id IN (:teamIds) AND f.status IN ('Open', 'Accepted')
          AND f.purpose = 'Assist'
        ORDER BY f.dispatched_at, f.id
        """)
        .param(EVENT_ID, eventId)
        .param("teamIds", teamIds)
        .query((rs, row) -> new FieldTask(
            rs.getObject("task_id", UUID.class),
            rs.getObject("task_team_id", UUID.class),
            FieldTask.Status.valueOf(rs.getString("task_status")),
            rs.getString("task_accepted_by"),
            rs.getTimestamp("task_dispatched_at").toInstant(),
            rs.getTimestamp("task_updated_at").toInstant(),
            JdbcHelpRequests.map(rs, row)))
        .list();
  }
}
