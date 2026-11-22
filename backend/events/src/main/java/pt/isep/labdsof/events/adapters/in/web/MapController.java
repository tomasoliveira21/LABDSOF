package pt.isep.labdsof.events.adapters.in.web;

import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import pt.isep.labdsof.events.adapters.in.web.generated.api.MapApi;
import pt.isep.labdsof.events.adapters.in.web.generated.model.MapVersion;
import pt.isep.labdsof.events.adapters.in.web.generated.model.NewPointOfInterest;
import pt.isep.labdsof.events.adapters.in.web.generated.model.PointOfInterest;
import pt.isep.labdsof.events.adapters.in.web.generated.model.PointOfInterestChange;

@RestController
class MapController implements MapApi {

  @Override
  public ResponseEntity<MapVersion> uploadMapVersion(UUID eventId, MultipartFile file) {
    throw new NotYetImplementedException("US-19");
  }

  @Override
  public ResponseEntity<MapVersion> getCurrentMapVersion(UUID eventId) {
    throw new NotYetImplementedException("US-21 and US-22");
  }

  @Override
  public ResponseEntity<MapVersion> getMapVersion(UUID eventId, Integer version) {
    throw new NotYetImplementedException("US-27");
  }

  @Override
  public ResponseEntity<Resource> getMapImage(UUID eventId, Integer version) {
    throw new NotYetImplementedException("US-21");
  }

  @Override
  public ResponseEntity<PointOfInterest> createPointOfInterest(UUID eventId, Integer version,
      NewPointOfInterest newPointOfInterest) {
    throw new NotYetImplementedException("US-20");
  }

  @Override
  public ResponseEntity<PointOfInterest> updatePointOfInterest(UUID eventId, Integer version, UUID poiId,
      PointOfInterestChange pointOfInterestChange) {
    throw new NotYetImplementedException("US-20");
  }

  @Override
  public ResponseEntity<Void> deletePointOfInterest(UUID eventId, Integer version, UUID poiId) {
    throw new NotYetImplementedException("US-20");
  }
}
