package pt.isep.labdsof.events.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pt.isep.labdsof.events.domain.MapSourceType;
import pt.isep.labdsof.events.domain.Permission;
import pt.isep.labdsof.events.domain.PointOfInterest;
import pt.isep.labdsof.events.domain.VenueMapVersion;

/**
 * The venue map of an event (US-19): an EventAdmin, or a member of a team with {@code Map.Edit}, uploads an image
 * or a single-page PDF and it becomes the event's current version, with the live points of the version before it
 * copied onto it (D8), the PNG on the volume (D9), the {@code VenueMapVersionPublished} the other services read
 * (D10) and the line that records who uploaded it and when (D11).
 */
@Service
public class MapService {

  private final EventReader reader;
  private final VenueMaps maps;
  private final MapImages images;
  private final MapFiles files;
  private final Outbox outbox;
  private final MapAudit audit;
  private final Clock clock;

  MapService(EventReader reader, VenueMaps maps, MapImages images, MapFiles files, Outbox outbox, MapAudit audit,
      Clock clock) {
    this.reader = reader;
    this.maps = maps;
    this.images = images;
    this.files = files;
    this.outbox = outbox;
    this.audit = audit;
    this.clock = clock;
  }

  /**
   * Stores the upload as the event's next map version and answers it as the back office now sees it, with the
   * points it starts with. The file is checked and rendered <em>before</em> anything is written or locked (D1 to
   * D5), so a file that is not a map costs the event nothing — its current map stays, which is what AC-2 and AC-3
   * ask for.
   */
  @Transactional
  public VenueMapVersion upload(UUID userId, UUID eventId, byte[] content) {
    editor(userId, eventId);
    var type = MapSourceType.ofContent(content);
    VenueMapVersion.checkSourceSize(content.length);
    var png = images.toPng(content, type);
    var at = clock.instant();
    var version = maps.nextVersionOf(eventId);
    var map = VenueMapVersion.uploaded(eventId, version, type, content.length, at, userId);
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

  /** A member with {@code Map.Edit} of an event that still accepts changes (D6). */
  private void editor(UUID userId, UUID eventId) {
    if (!reader.with(userId, eventId, Permission.MAP_EDIT).event().status().acceptsChanges()) {
      throw new EventNotOpenException();
    }
  }
}
