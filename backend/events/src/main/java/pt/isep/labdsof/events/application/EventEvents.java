package pt.isep.labdsof.events.application;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import pt.isep.labdsof.events.domain.Event;

/**
 * EventChanged, as contracts/events/event.changed.schema.json describes it (API outline §7.4): the full state an
 * event's consumers need, with its version, published whenever the event is created, edited, published, suspended,
 * reactivated or closed. US-11 is the first story to publish it (spec D5).
 *
 * <p>Neither the description nor the join code is carried: no consumer needs them, and the join code is for the
 * event's own members (spec #192 D7).
 */
public final class EventEvents {

  static final String ROUTING_KEY = "event.changed";
  static final String TYPE = "EventChanged";
  static final int SCHEMA_VERSION = 1;

  private EventEvents() {}

  public static OutgoingEvent changed(Event event, UUID actor, Instant at) {
    return new OutgoingEvent(UUID.randomUUID(), OutgoingEvent.EXCHANGE, ROUTING_KEY, TYPE, SCHEMA_VERSION, at,
        event.eventId(), actor.toString(), payload(event));
  }

  public static Map<String, Object> payload(Event event) {
    var payload = new LinkedHashMap<String, Object>();
    payload.put("name", event.name());
    payload.put("organiserName", event.organiserName());
    payload.put("dates", Map.of("start", event.dates().start().toString(), "end", event.dates().end().toString()));
    payload.put("status", event.status().name());
    payload.put("version", event.version());
    return payload;
  }
}
