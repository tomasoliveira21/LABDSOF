package pt.isep.labdsof.authusers.adapters.out.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import pt.isep.labdsof.authusers.application.EventCopies;
import pt.isep.labdsof.authusers.application.MyMemberships.Status;

@Repository
class JdbcEventCopies implements EventCopies {

  private final JdbcClient jdbc;

  JdbcEventCopies(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void event(UUID eventId, String name, Status status, long version) {
    jdbc.sql("""
        INSERT INTO event_copy (event_id, name, status, version)
        VALUES (:id, :name, :status, :version)
        ON CONFLICT (event_id) DO UPDATE
        SET name = excluded.name, status = excluded.status, version = excluded.version
        WHERE event_copy.version < excluded.version
        """)
        .param("id", eventId).param("name", name).param("status", status.name()).param("version", version)
        .update();
  }

  @Override
  public Optional<Copy> find(UUID eventId) {
    return jdbc.sql("SELECT event_id, name, status FROM event_copy WHERE event_id = :id")
        .param("id", eventId)
        .query((row, number) -> new Copy(row.getObject("event_id", UUID.class), row.getString("name"),
            Status.valueOf(row.getString("status"))))
        .optional();
  }

  @Override
  public void team(UUID eventId, UUID teamId, String payload, long version) {
    jdbc.sql("""
        INSERT INTO event_team_copy (team_id, event_id, payload, version)
        VALUES (:team, :event, CAST(:payload AS jsonb), :version)
        ON CONFLICT (team_id) DO UPDATE
        SET payload = excluded.payload, version = excluded.version
        WHERE event_team_copy.event_id = excluded.event_id AND event_team_copy.version < excluded.version
        """)
        .param("event", eventId).param("team", teamId).param("payload", payload).param("version", version)
        .update();
  }
}
