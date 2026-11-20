package pt.isep.labdsof.reports.adapters.out.persistence;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import pt.isep.labdsof.reports.application.Outbox;
import pt.isep.labdsof.reports.application.OutgoingEvent;
import tools.jackson.databind.json.JsonMapper;

/** The outbox table (spec D4). {@link #add} runs in the caller's transaction; the rest is for the publisher. */
@Repository
public class JdbcOutbox implements Outbox {

  /** How much of a broker error is kept in {@code last_error} (#191: the same bound as Auth & Users'). */
  public static final int MAX_ERROR_LENGTH = 500;

  /** One row claimed for publishing. */
  public record Pending(long id, UUID messageId, String exchange, String routingKey, String type,
      String body, int attempts, String correlationId) {}

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
        INSERT INTO outbox (message_id, exchange, routing_key, type, body, correlation_id)
        VALUES (:messageId, :exchange, :routingKey, :type, CAST(:body AS jsonb), :correlationId)
        """)
        .param("messageId", event.messageId())
        .param("exchange", event.exchange())
        .param("routingKey", event.routingKey())
        .param("type", event.type())
        .param("body", json.writeValueAsString(envelope))
        .param("correlationId", MDC.get("correlationId")) // #148: the request's, set by CorrelationIdFilter
        .update();
  }

  /** Locks up to {@code limit} due rows in publishing order; rows locked by another publisher are skipped. */
  public List<Pending> claimDue(int limit) {
    return jdbc.sql("""
        SELECT id, message_id, exchange, routing_key, type, body::text AS body, attempts, correlation_id
        FROM outbox
        WHERE published_at IS NULL AND next_attempt_at <= now()
        ORDER BY id
        LIMIT :limit
        FOR UPDATE SKIP LOCKED
        """)
        .param("limit", limit)
        .query((rs, row) -> new Pending(rs.getLong("id"), rs.getObject("message_id", UUID.class),
            rs.getString("exchange"), rs.getString("routing_key"), rs.getString("type"),
            rs.getString("body"), rs.getInt("attempts"), rs.getString("correlation_id")))
        .list();
  }

  /** #148: seconds since the oldest unpublished row was written, 0 when none is pending (the outbox alert). */
  public double oldestPendingSeconds() {
    return jdbc.sql("SELECT COALESCE(EXTRACT(EPOCH FROM now() - min(created_at)), 0) FROM outbox "
        + "WHERE published_at IS NULL").query(Double.class).single();
  }

  public void markPublished(long id, Instant at) {
    jdbc.sql("UPDATE outbox SET published_at = :at, last_error = NULL WHERE id = :id")
        .param("at", Timestamp.from(at))
        .param("id", id)
        .update();
  }

  /** The next attempt is due {@code backoff} from the database's clock, the one {@link #claimDue} compares. */
  public void markFailed(long id, int attempts, Duration backoff, String error) {
    jdbc.sql("""
        UPDATE outbox SET attempts = :attempts, last_error = :error,
               next_attempt_at = clock_timestamp() + make_interval(secs => :seconds)
        WHERE id = :id
        """)
        .param("attempts", attempts)
        .param("seconds", backoff.toSeconds())
        .param("error", bounded(error))
        .param("id", id)
        .update();
  }

  /**
   * PostgreSQL text cannot hold NUL, and a failing {@code markFailed} would roll back the batch and leave the row
   * without backoff: NUL becomes U+FFFD, and the text is cut to {@link #MAX_ERROR_LENGTH} code points, never inside a
   * surrogate pair. Ported from Auth & Users (#191, Bruno's review of PR 215); the duplication itself is #226.
   */
  static String bounded(String error) {
    var clean = error.replace('\u0000', '�');
    if (clean.codePointCount(0, clean.length()) <= MAX_ERROR_LENGTH) {
      return clean;
    }
    return clean.substring(0, clean.offsetByCodePoints(0, MAX_ERROR_LENGTH));
  }
}
