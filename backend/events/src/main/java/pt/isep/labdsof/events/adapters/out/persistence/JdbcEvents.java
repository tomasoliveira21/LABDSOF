package pt.isep.labdsof.events.adapters.out.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import pt.isep.labdsof.events.application.Events;
import pt.isep.labdsof.events.domain.DateRange;
import pt.isep.labdsof.events.domain.Event;
import pt.isep.labdsof.events.domain.EventStatus;

@Repository
class JdbcEvents implements Events {

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
        .param("eventId", eventId)
        .query(JdbcEvents::event)
        .optional();
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
