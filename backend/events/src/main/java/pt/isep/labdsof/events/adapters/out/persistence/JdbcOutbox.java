package pt.isep.labdsof.events.adapters.out.persistence;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import pt.isep.labdsof.events.Correlation;
import pt.isep.labdsof.events.application.Outbox;
import pt.isep.labdsof.events.application.OutgoingEvent;
import tools.jackson.databind.json.JsonMapper;

/**
 * The outbox table (spec D4, ADR-004). {@link #add} runs in the caller's transaction; the rest is for the
 * publisher, inside its transaction.
 */
@Repository
public class JdbcOutbox implements Outbox {

  /** Longest {@code last_error} stored, in characters (code points). */
  public static final int MAX_ERROR_LENGTH = 500;

  private static final String ID = "id";

  /** One row claimed for publishing. */
  public record Pending(long id, UUID messageId, String exchange, String routingKey, String type,
      String body, String correlationId, int attempts) {}

  private final JdbcClient jdbc;
  private final JsonMapper json;

  JdbcOutbox(JdbcClient jdbc, JsonMapper json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  @Override
  public void add(OutgoingEvent event) {
    jdbc.sql("""
        INSERT INTO outbox (message_id, exchange, routing_key, type, body, correlation_id)
        VALUES (:messageId, :exchange, :routingKey, :type, CAST(:body AS jsonb), :correlationId)
        """)
        .param("messageId", event.messageId())
        .param("exchange", event.exchange())
        .param("routingKey", event.routingKey())
        .param("type", event.type())
        .param("body", json.writeValueAsString(event.envelope()))
        .param("correlationId", MDC.get(Correlation.MDC_KEY))
        .update();
  }

  /**
   * Locks up to {@code limit} due rows in publishing order. Call inside a transaction: the locks last until
   * it ends, and rows locked by another publisher are skipped.
   */
  public List<Pending> claimDue(int limit) {
    return jdbc.sql("""
        SELECT id, message_id, exchange, routing_key, type, body::text AS body, correlation_id, attempts
        FROM outbox
        WHERE published_at IS NULL AND next_attempt_at <= now()
        ORDER BY id
        LIMIT :limit
        FOR UPDATE SKIP LOCKED
        """)
        .param("limit", limit)
        .query((rs, row) -> new Pending(rs.getLong(ID), rs.getObject("message_id", UUID.class),
            rs.getString("exchange"), rs.getString("routing_key"), rs.getString("type"),
            rs.getString("body"), rs.getString("correlation_id"), rs.getInt("attempts")))
        .list();
  }

  public void markPublished(long id, Instant at) {
    jdbc.sql("UPDATE outbox SET published_at = :at, last_error = NULL WHERE id = :id")
        .param("at", Timestamp.from(at))
        .param(ID, id)
        .update();
  }

  public void markFailed(long id, int attempts, Instant nextAttemptAt, String error) {
    jdbc.sql("""
        UPDATE outbox SET attempts = :attempts, next_attempt_at = :next, last_error = :error WHERE id = :id
        """)
        .param("attempts", attempts)
        .param("next", Timestamp.from(nextAttemptAt))
        .param("error", bounded(error))
        .param(ID, id)
        .update();
  }

  /**
   * PostgreSQL text cannot hold NUL, and a failing {@code markFailed} would roll back the batch and leave
   * the row without backoff: NUL becomes U+FFFD, and the text is cut to {@link #MAX_ERROR_LENGTH} code
   * points, never inside a surrogate pair.
   */
  static String bounded(String error) {
    var clean = error.replace('\u0000', '�');
    if (clean.codePointCount(0, clean.length()) <= MAX_ERROR_LENGTH) {
      return clean;
    }
    return clean.substring(0, clean.offsetByCodePoints(0, MAX_ERROR_LENGTH));
  }
}
