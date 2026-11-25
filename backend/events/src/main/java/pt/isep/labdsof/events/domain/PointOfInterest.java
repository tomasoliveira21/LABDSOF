package pt.isep.labdsof.events.domain;

import java.util.Objects;
import java.util.UUID;

/**
 * A point placed on one map version (US-20 places and moves them; US-19 only copies the live ones onto the version
 * it creates). Soft deleted with a version, because {@code PointOfInterestChanged} carries {@code deleted} (API
 * outline §7.4, spec #192 D4).
 */
public record PointOfInterest(UUID poiId, UUID eventId, int mapVersion, PoiType type, String name,
    MapPosition position, boolean deleted, long version) {

  /** The database's point_of_interest_name check: 1 to 100 characters once trimmed. */
  public static final int MAX_NAME_LENGTH = 100;

  public PointOfInterest {
    Objects.requireNonNull(poiId, "poiId");
    Objects.requireNonNull(eventId, "eventId");
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(position, "position");
    name = trimmedName(name);
    if (mapVersion < 1) {
      throw new IllegalArgumentException("mapVersion must be at least 1");
    }
    if (version < 1) {
      throw new IllegalArgumentException("version must be at least 1");
    }
  }

  /**
   * The same point on a new map version (US-19 D8): a new id, version 1 and the member who uploaded the map as the
   * one who wrote the row, keeping the type, the name and the position. A deleted point is never copied, so this
   * always makes a live one.
   */
  public PointOfInterest copiedTo(int newMapVersion, UUID newPoiId) {
    return new PointOfInterest(newPoiId, eventId, newMapVersion, type, name, position, false, 1);
  }

  private static String trimmedName(String name) {
    var trimmed = Objects.requireNonNull(name, "name").trim();
    if (trimmed.isEmpty() || trimmed.length() > MAX_NAME_LENGTH) {
      throw new IllegalArgumentException("a point name has 1 to 100 characters");
    }
    return trimmed;
  }
}
