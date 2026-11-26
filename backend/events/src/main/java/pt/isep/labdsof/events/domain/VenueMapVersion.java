package pt.isep.labdsof.events.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * One version of an event's venue map (US-19). Only the rendered PNG is kept, on a volume of the service, and this
 * row holds its name plus what the upload was (API outline D6, spec #192's table). Versions start at 1 and only
 * ever grow, so the help requests and reports that name one keep pointing at the map they were placed on (US-27).
 */
public record VenueMapVersion(UUID eventId, int version, String imageFile, MapSourceType sourceType,
    long sourceSizeBytes, Instant uploadedAt, UUID uploadedBy, List<PointOfInterest> pointsOfInterest) {

  /** ADR-013 L-4, and {@code venue_map_version_source_size}: 20 MB. */
  public static final long MAX_SOURCE_SIZE_BYTES = 20L * 1024 * 1024;

  public VenueMapVersion {
    Objects.requireNonNull(eventId, "eventId");
    Objects.requireNonNull(sourceType, "sourceType");
    Objects.requireNonNull(uploadedAt, "uploadedAt");
    Objects.requireNonNull(uploadedBy, "uploadedBy");
    imageFile = trimmedFile(imageFile);
    pointsOfInterest = List.copyOf(pointsOfInterest);
    if (version < 1) {
      throw new IllegalArgumentException("version must be at least 1");
    }
    checkSourceSize(sourceSizeBytes);
  }

  /**
   * The 20 MB of ADR-013 L-4 (D5), checked before the file is decoded as well as here: an empty file is not a map
   * at all, and a file above the limit is the {@code 413} AC-3 asks for. The multipart limit of the service
   * normally refuses it first, without ever reading it.
   */
  public static void checkSourceSize(long sourceSizeBytes) {
    if (sourceSizeBytes < 1) {
      throw new InvalidMapFileException("empty file");
    }
    if (sourceSizeBytes > MAX_SOURCE_SIZE_BYTES) {
      throw new MapFileTooLargeException();
    }
  }

  /**
   * The version an upload creates: the next number of that event, no points yet — the live points of the version
   * before it are copied onto it by the use case (D8).
   */
  public static VenueMapVersion uploaded(UUID eventId, int version, MapSourceType sourceType, long sourceSizeBytes,
      Instant at, UUID uploadedBy) {
    return new VenueMapVersion(eventId, version, imageFileOf(eventId, version), sourceType, sourceSizeBytes, at,
        uploadedBy, List.of());
  }

  /** The same version with the points it starts with (D8), in the order they are read. */
  public VenueMapVersion with(List<PointOfInterest> points) {
    return new VenueMapVersion(eventId, version, imageFile, sourceType, sourceSizeBytes, uploadedAt, uploadedBy,
        points);
  }

  /** Where the PNG lives, under the volume's directory: one folder per event (D9). */
  public static String imageFileOf(UUID eventId, int version) {
    return eventId + "/" + version + ".png";
  }

  private static String trimmedFile(String imageFile) {
    var trimmed = Objects.requireNonNull(imageFile, "imageFile").trim();
    if (trimmed.isEmpty()) {
      throw new IllegalArgumentException("imageFile must not be blank");
    }
    return trimmed;
  }
}
