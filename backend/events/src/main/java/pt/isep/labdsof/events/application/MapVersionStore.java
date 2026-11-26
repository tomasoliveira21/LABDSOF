package pt.isep.labdsof.events.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.isep.labdsof.events.domain.MapSourceType;
import pt.isep.labdsof.events.domain.PointOfInterest;
import pt.isep.labdsof.events.domain.VenueMapVersion;

/**
 * The write of a map upload (US-19 D8 to D11): the version, the points copied onto it, the event it publishes and
 * the line it logs, in one transaction that takes the event's row with it.
 *
 * <p>It is a bean of its own, and not a method of {@link MapService}, so the transaction starts <em>after</em> the
 * upload is decoded: {@code @Transactional} takes a connection out of a pool of five for as long as the method
 * runs, and a PDF may render for up to 20 s (D4). Five of them at once would leave the service — its listener and
 * its outbox included — without a connection for a decode that needs none (review of #253).
 */
@Service
public class MapVersionStore {

  private final VenueMaps maps;
  private final MapFiles files;
  private final Outbox outbox;
  private final MapAudit audit;

  MapVersionStore(VenueMaps maps, MapFiles files, Outbox outbox, MapAudit audit) {
    this.maps = maps;
    this.files = files;
    this.outbox = outbox;
    this.audit = audit;
  }

  /**
   * Stores {@code png} as the event's next version. {@code stillAnEditor} is the caller's check of D6, run again
   * once the event's row is locked: the decode before this call is not instant, and an event suspended meanwhile
   * takes no map.
   *
   * @param at the instant the upload was accepted, so the version, its points and its log line all carry it
   */
  @Transactional
  public VenueMapVersion store(UUID userId, UUID eventId, MapSourceType type, int sourceSizeBytes, byte[] png,
      Instant at, Runnable stillAnEditor) {
    var version = maps.nextVersionOf(eventId); // locks the event's row
    stillAnEditor.run();
    var map = VenueMapVersion.uploaded(eventId, version, type, sourceSizeBytes, at, userId);
    files.write(map.imageFile(), png);
    try {
      return stored(map, userId, at);
    } catch (RuntimeException e) {
      // The row was not stored, so nothing will ever read this file (D9)
      files.delete(map.imageFile());
      throw e;
    }
  }

  /** The version, the points it starts with, the event it publishes and the line it logs: one transaction (D10). */
  private VenueMapVersion stored(VenueMapVersion map, UUID userId, Instant at) {
    maps.add(map); // before the points: each one belongs to this version (point_of_interest_map_version)
    var points = copied(map, userId, at);
    outbox.add(MapEvents.versionPublished(map));
    audit.uploaded(userId, map.eventId(), at, map.version(), map.sourceType(), map.sourceSizeBytes());
    return map.with(points);
  }

  /**
   * The live points of the version before this one, on this one: new ids, version 1 and this uploader (D8). The
   * first version of an event starts empty, and so does one whose predecessor had every point deleted.
   */
  private List<PointOfInterest> copied(VenueMapVersion map, UUID userId, Instant at) {
    var points = maps.livePointsOf(map.eventId(), map.version() - 1).stream()
        .map(point -> point.copiedTo(map.version(), UUID.randomUUID()))
        .toList();
    maps.addPoints(points, userId, at);
    return points;
  }
}
