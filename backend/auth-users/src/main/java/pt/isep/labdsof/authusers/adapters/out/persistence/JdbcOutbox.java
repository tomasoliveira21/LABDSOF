package pt.isep.labdsof.authusers.adapters.out.persistence;

import org.slf4j.MDC;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import pt.isep.labdsof.authusers.application.Outbox;
import pt.isep.labdsof.authusers.application.OutgoingEvent;
import tools.jackson.databind.json.JsonMapper;

/** The outbox table (spec D3). {@link #add} runs in the caller's transaction. */
@Repository
public class JdbcOutbox implements Outbox {

  /** The MDC key of the request's correlation id (spec D8); the web filter uses the same one. */
  public static final String CORRELATION_ID_MDC_KEY = "correlationId";

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
        .param("correlationId", MDC.get(CORRELATION_ID_MDC_KEY))
        .update();
  }
}
