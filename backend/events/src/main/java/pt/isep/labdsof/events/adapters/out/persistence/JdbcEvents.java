package pt.isep.labdsof.events.adapters.out.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import pt.isep.labdsof.events.application.Events;
import pt.isep.labdsof.events.domain.DateRange;
import pt.isep.labdsof.events.domain.Event;
import pt.isep.labdsof.events.domain.EventStatus;

/**
 * The event table (spec #192 D3). The publish carries the status <em>and</em> the version it read, so the row it
 * changes is the draft the use case saw: without that, two publishes starting from the same read both commit and
 * publish two EventChanged carrying one version, and a consumer that applies only a strictly newer version keeps
 * whichever arrived first (US-11 D4, decision 3 of 21 November 2026). The one transition is therefore atomic, and
 * no event can end up with two join codes.
 */
@Repository
class JdbcEvents implements Events {

  private static final String EVENT_ID = "eventId";
  private static final String VERSION = "version";

  private final JdbcClient jdbc;

  JdbcEvents(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Optional<Event> findById(UUID eventId) {
    return jdbc.sql("""
        SELECT event_id, name, organiser_name, start_date, end_date, description, status, join_code, version
        FROM event WHERE event_id = :eventId
        """)
        .param(EVENT_ID, eventId)
        .query(JdbcEvents::event)
        .optional();
  }

  @Override
  public Write publish(Event event, UUID actor, Instant at) {
    // A join code already in the table (event_join_code_unique) lets the unique violation roll the transaction
    // back, publishing nothing: at 2⁻¹⁶⁰ per publish it is not worth a savepoint (US-11 D1).
    return jdbc.sql("""
        UPDATE event SET status = 'Published', join_code = :code, version = :version, updated_at = :at,
                         updated_by = :actor
        WHERE event_id = :eventId AND status = 'Draft' AND version = :version - 1
        """)
        .param("code", event.joinCode())
        .param(VERSION, event.version())
        .param("at", Timestamp.from(at))
        .param("actor", actor)
        .param(EVENT_ID, event.eventId())
        .update() == 1 ? Write.STORED : missed(event);
  }

  /**
   * Why the publish changed no row: there is no such event, it is no longer a draft, or it is still a draft at
   * another version, which is a lost race against a publish this one never saw. Read in the same transaction as
   * the UPDATE that missed, so it sees the row that blocked it.
   */
  private Write missed(Event event) {
    return jdbc.sql("SELECT status FROM event WHERE event_id = :eventId")
        .param(EVENT_ID, event.eventId())
        .query(String.class)
        .optional()
        .map(status -> EventStatus.Draft.name().equals(status) ? Write.CONFLICT : Write.NOT_DRAFT)
        .orElse(Write.GONE);
  }

  private static Event event(ResultSet rs, int row) throws SQLException {
    return new Event(
        rs.getObject("event_id", UUID.class),
        rs.getString("name"),
        rs.getString("organiser_name"),
        new DateRange(rs.getObject("start_date", LocalDate.class), rs.getObject("end_date", LocalDate.class)),
        rs.getString("description"),
        EventStatus.valueOf(rs.getString("status")),
        rs.getString("join_code"),
        rs.getLong("version"));
  }
}
