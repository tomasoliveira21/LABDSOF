package pt.isep.labdsof.events.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import pt.isep.labdsof.events.domain.PointOfInterest;
import pt.isep.labdsof.events.domain.VenueMapVersion;

/** Port: the venue map versions and their points, in this service's own database (ADR-003). */
public interface VenueMaps {

  /**
   * The number the next version of that event gets, with the event row locked for the rest of the transaction
   * (US-19 D7), so two uploads of one event queue instead of both reading the same maximum. Uploads of different
   * events never wait for each other.
   */
  int nextVersionOf(UUID eventId);

  /** Stores the version. Its number comes from {@link #nextVersionOf}, in the same transaction. */
  void add(VenueMapVersion version);

  /** The live points of one version, oldest first, or nothing for a version that has none (or does not exist). */
  List<PointOfInterest> livePointsOf(UUID eventId, int mapVersion);

  /** Stores the points a new version starts with (D8), recorded as written by the member who uploaded it. */
  void addPoints(List<PointOfInterest> points, UUID actor, Instant at);
}
