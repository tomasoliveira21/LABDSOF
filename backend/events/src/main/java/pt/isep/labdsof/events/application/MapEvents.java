package pt.isep.labdsof.events.application;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import pt.isep.labdsof.events.domain.VenueMapVersion;

/**
 * VenueMapVersionPublished, as contracts/events/venue-map-version.published.schema.json describes it (API outline
 * §7.4): a new version of an event's venue map exists. US-19 is the first story to publish it, and the first
 * story of the product to publish it at all (spec D10).
 *
 * <p>Only the version and the time: Reports needs no more to check that a help request or a report points at a
 * map version of its event (US-23, US-47), and the image itself is read through the API.
 */
public final class MapEvents {

  static final String ROUTING_KEY = "venue-map-version.published";
  static final String TYPE = "VenueMapVersionPublished";
  static final int SCHEMA_VERSION = 1;

  private MapEvents() {}

  public static OutgoingEvent versionPublished(VenueMapVersion version) {
    return new OutgoingEvent(UUID.randomUUID(), OutgoingEvent.EXCHANGE, ROUTING_KEY, TYPE, SCHEMA_VERSION,
        version.uploadedAt(), version.eventId(), version.uploadedBy().toString(), payload(version));
  }

  public static Map<String, Object> payload(VenueMapVersion version) {
    var payload = new LinkedHashMap<String, Object>();
    payload.put("mapVersion", version.version());
    payload.put("publishedAt", version.uploadedAt().toString());
    return payload;
  }
}
