package pt.isep.labdsof.events.domain;

/**
 * The uploaded file is above the 20 MB of ADR-013 L-4 (US-19 AC-3, D5). The multipart limit of the service refuses
 * it before it is ever read; this is the same rule in the domain, for any other way into the use case.
 */
public class MapFileTooLargeException extends RuntimeException {

  public MapFileTooLargeException() {
    super("a map file is at most " + VenueMapVersion.MAX_SOURCE_SIZE_BYTES + " bytes");
  }
}
