package pt.isep.labdsof.transports.application;

import pt.isep.labdsof.transports.domain.ImportOutcome;

/** What the source answered to one read (spec D5); {@link SourceImportService} records it with its times. */
public record SourceReading(ImportOutcome outcome, Integer vehicleCount, String error) {

  public static SourceReading succeeded(int vehicleCount) {
    return new SourceReading(ImportOutcome.Succeeded, vehicleCount, null);
  }

  /** Timeout, connection error or HTTP error. */
  public static SourceReading unavailable(String reason) {
    return new SourceReading(ImportOutcome.Unavailable, null, reason);
  }

  /** An answer that is not a list of NGSI entities. */
  public static SourceReading invalid(String reason) {
    return new SourceReading(ImportOutcome.Invalid, null, reason);
  }
}
