package pt.isep.labdsof.reports.adapters.out.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import pt.isep.labdsof.reports.application.FieldTasks;

/** The field tasks (spec US-29 D4). Every statement is filtered by event (ADR-003 decision 3). */
@Repository
class JdbcFieldTasks implements FieldTasks {

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
        .param("eventId", eventId)
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
        .param("eventId", eventId)
        .param("requestId", requestId)
        .param("at", Timestamp.from(at))
        .update();
  }
}
