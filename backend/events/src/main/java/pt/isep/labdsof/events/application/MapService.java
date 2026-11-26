package pt.isep.labdsof.events.application;

import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import pt.isep.labdsof.events.domain.MapSourceType;
import pt.isep.labdsof.events.domain.Permission;
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
  private final MapImages images;
  private final MapVersionStore store;
  private final Clock clock;

  MapService(EventReader reader, MapImages images, MapVersionStore store, Clock clock) {
    this.reader = reader;
    this.images = images;
    this.store = store;
    this.clock = clock;
  }

  /**
   * Stores the upload as the event's next map version and answers it as the back office now sees it, with the
   * points it starts with. The file is checked and rendered <em>before</em> anything is written, locked or even
   * connected to (D1 to D5, {@link MapVersionStore}), so a file that is not a map costs the event nothing — its
   * current map stays, which is what AC-2 and AC-3 ask for.
   */
  public VenueMapVersion upload(UUID userId, UUID eventId, byte[] content) {
    editor(userId, eventId);
    var type = MapSourceType.ofContent(content);
    VenueMapVersion.checkSourceSize(content.length);
    var png = images.toPng(content, type);
    return store.store(userId, eventId, type, content.length, png, clock.instant(),
        () -> editor(userId, eventId));
  }

  /** A member with {@code Map.Edit} of an event that still accepts changes (D6). */
  private void editor(UUID userId, UUID eventId) {
    if (!reader.with(userId, eventId, Permission.MAP_EDIT).event().status().acceptsChanges()) {
      throw new EventNotOpenException();
    }
  }
}
