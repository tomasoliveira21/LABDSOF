package pt.isep.labdsof.transports.adapters.out.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import pt.isep.labdsof.transports.application.TransportOptions;
import pt.isep.labdsof.transports.domain.BusRoute;
import pt.isep.labdsof.transports.domain.EventStatus;

/** The external bus routes in {@code transport_option} (spec US-41 D4; #193 V1, US-41 V2). */
@Repository
class JdbcTransportOptions implements TransportOptions {

  private static final String EVENT_ID = "eventId";

  private final JdbcClient jdbc;

  JdbcTransportOptions(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Added addBusRoute(BusRoute route, int limit) {
    // Serialises the adds of one event, so the limit holds for requests at the same time (D4), and reads its status
    // again under the lock: an EventChanged applied after the caller's access check still counts (D3)
    var status = jdbc.sql("SELECT status FROM event_copy WHERE event_id = :eventId FOR UPDATE")
        .param(EVENT_ID, route.eventId()).query(String.class).optional();
    if (status.isEmpty() || EventStatus.Suspended.name().equals(status.get())) {
      return Added.GONE;
    }
    if (EventStatus.Closed.name().equals(status.get())) {
      return Added.CLOSED;
    }
    var held = jdbc.sql("SELECT count(*) FROM transport_option WHERE event_id = :eventId AND mode = 'ExternalBus'")
        .param(EVENT_ID, route.eventId()).query(Integer.class).single();
    if (held >= limit) {
      return Added.FULL;
    }
    var inserted = jdbc.sql("""
        INSERT INTO transport_option (option_id, event_id, mode, name, external_route, version, created_at,
                                      last_updated_at, last_updated_by)
        VALUES (:optionId, :eventId, 'ExternalBus', :name, :route, 1, :at, :at, :by)
        ON CONFLICT (event_id, external_route) WHERE mode = 'ExternalBus' DO NOTHING
        """)
        .param("optionId", route.optionId())
        .param(EVENT_ID, route.eventId())
        .param("name", route.name())
        .param("route", route.externalRoute())
        .param("at", Timestamp.from(route.addedAt()))
        .param("by", route.addedBy())
        .update();
    return inserted == 1 ? Added.ADDED : Added.TAKEN;
  }

  @Override
  public List<BusRoute> busRoutes(UUID eventId) {
    return jdbc.sql("""
        SELECT option_id, event_id, external_route, name, created_at, last_updated_by
        FROM transport_option WHERE event_id = :eventId AND mode = 'ExternalBus'
        ORDER BY created_at, option_id
        """)
        .param(EVENT_ID, eventId)
        .query(JdbcTransportOptions::route)
        .list();
  }

  private static BusRoute route(ResultSet rs, int row) throws SQLException {
    return new BusRoute(rs.getObject("option_id", UUID.class), rs.getObject("event_id", UUID.class),
        rs.getString("external_route"), rs.getString("name"), rs.getTimestamp("created_at").toInstant(),
        rs.getObject("last_updated_by", UUID.class));
  }
}
