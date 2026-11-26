package pt.isep.labdsof.events.adapters.in.web;

import java.io.IOException;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import pt.isep.labdsof.events.adapters.in.web.generated.api.MapApi;
import pt.isep.labdsof.events.adapters.in.web.generated.model.MapVersion;
import pt.isep.labdsof.events.adapters.in.web.generated.model.NewPointOfInterest;
import pt.isep.labdsof.events.adapters.in.web.generated.model.PointOfInterest;
import pt.isep.labdsof.events.adapters.in.web.generated.model.PointOfInterestChange;
import pt.isep.labdsof.events.application.MapService;
import pt.isep.labdsof.events.domain.InvalidMapFileException;

@RestController
class MapController implements MapApi {

  private final MapService maps;

  MapController(MapService maps) {
    this.maps = maps;
  }

  @Override
  public ResponseEntity<MapVersion> uploadMapVersion(UUID eventId, MultipartFile file) {
    var uploaded = maps.upload(TokenIdentity.currentUserId(), eventId, content(file));
    return ResponseEntity.status(HttpStatus.CREATED).body(MapResponse.of(uploaded));
  }

  /**
   * The bytes of the one file part. A part that cannot be read is not a map either, so it is the same {@code 400}
   * (US-19 D1); the limit of 20 MB is the multipart configuration's, which answers before this is ever called.
   */
  static byte[] content(MultipartFile file) {
    try {
      return file.getBytes();
    } catch (IOException e) {
      throw new InvalidMapFileException("the uploaded part could not be read", e);
    }
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
