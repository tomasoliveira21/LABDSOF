package pt.isep.labdsof.reports.adapters.out.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import pt.isep.labdsof.reports.application.Outbox;
import pt.isep.labdsof.reports.application.OutgoingEvent;
import tools.jackson.databind.json.JsonMapper;

/** The outbox table (spec D4). {@link #add} runs in the caller's transaction; the rest is for the publisher. */
@Repository
public class JdbcOutbox implements Outbox {

  /** One row claimed for publishing. */
  public record Pending(long id, UUID messageId, String exchange, String routingKey, String type,
      String body, int attempts) {}

  private final JdbcClient jdbc;
  private final JsonMapper json;

  JdbcOutbox(JdbcClient jdbc, JsonMapper json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  @Override
  public void add(OutgoingEvent event) {
    // The envelope of API outline §7.1, built when the business change happens.
    var envelope = new LinkedHashMap<String, Object>();
    envelope.put("messageId", event.messageId().toString());
    envelope.put("type", event.type());
    envelope.put("schemaVersion", event.schemaVersion());
    envelope.put("occurredAt", event.occurredAt().toString());
    envelope.put("producer", OutgoingEvent.PRODUCER);
    envelope.put("eventId", event.eventId().toString());
    envelope.put("actorUserId", event.actorUserId());
    envelope.put("payload", event.payload());
    jdbc.sql("""
        INSERT INTO outbox (message_id, exchange, routing_key, type, body)
        VALUES (:messageId, :exchange, :routingKey, :type, CAST(:body AS jsonb))
        """)
        .param("messageId", event.messageId())
        .param("exchange", event.exchange())
        .param("routingKey", event.routingKey())
        .param("type", event.type())
        .param("body", json.writeValueAsString(envelope))
        .update();
  }

  /** Locks up to {@code limit} due rows in publishing order; rows locked by another publisher are skipped. */
  public List<Pending> claimDue(int limit) {
    return jdbc.sql("""
        SELECT id, message_id, exchange, routing_key, type, body::text AS body, attempts
        FROM outbox
        WHERE published_at IS NULL AND next_attempt_at <= now()
        ORDER BY id
        LIMIT :limit
        FOR UPDATE SKIP LOCKED
        """)
        .param("limit", limit)
        .query((rs, row) -> new Pending(rs.getLong("id"), rs.getObject("message_id", UUID.class),
            rs.getString("exchange"), rs.getString("routing_key"), rs.getString("type"),
            rs.getString("body"), rs.getInt("attempts")))
        .list();
  }

  public void markPublished(long id, Instant at) {
    jdbc.sql("UPDATE outbox SET published_at = :at, last_error = NULL WHERE id = :id")
        .param("at", Timestamp.from(at))
        .param("id", id)
        .update();
  }

  public void markFailed(long id, int attempts, Instant nextAttemptAt, String error) {
    jdbc.sql("""
        UPDATE outbox SET attempts = :attempts, next_attempt_at = :next, last_error = :error WHERE id = :id
        """)
        .param("attempts", attempts)
        .param("next", Timestamp.from(nextAttemptAt))
        .param("error", error)
        .param("id", id)
        .update();
  }
}
