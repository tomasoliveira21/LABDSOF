package pt.isep.labdsof.events.adapters.in.web;

import java.time.ZoneOffset;
import pt.isep.labdsof.events.adapters.in.web.generated.model.MapPosition;
import pt.isep.labdsof.events.adapters.in.web.generated.model.MapVersion;
import pt.isep.labdsof.events.adapters.in.web.generated.model.PoiType;
import pt.isep.labdsof.events.adapters.in.web.generated.model.PointOfInterest;
import pt.isep.labdsof.events.domain.VenueMapVersion;

/** Maps a {@link VenueMapVersion} to the contract's MapVersion. No business rule here. */
final class MapResponse {

  private MapResponse() {}

  static MapVersion of(VenueMapVersion version) {
    return new MapVersion(version.version(), version.uploadedAt().atOffset(ZoneOffset.UTC),
        version.pointsOfInterest().stream().map(MapResponse::point).toList());
  }

  private static PointOfInterest point(pt.isep.labdsof.events.domain.PointOfInterest point) {
    return new PointOfInterest(point.poiId(), PoiType.fromValue(point.type().name()), point.name(),
        new MapPosition(point.position().x(), point.position().y()));
  }
}
