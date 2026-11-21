package pt.isep.labdsof.transports.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * One attempt to read the bus source (spec D5): {@code Succeeded} with the number of vehicles, or
 * {@code Unavailable} / {@code Invalid} with the reason. The reason is stored without NUL (PostgreSQL refuses it) and
 * cut to 500 characters, as the column allows.
 */
public record SourceImport(Instant startedAt, Instant finishedAt, ImportOutcome outcome, Integer vehicleCount,
    String error) {

  static final int MAX_ERROR = 500;

  public SourceImport {
    Objects.requireNonNull(startedAt, "startedAt");
    Objects.requireNonNull(finishedAt, "finishedAt");
    Objects.requireNonNull(outcome, "outcome");
    if (finishedAt.isBefore(startedAt)) {
      throw new IllegalArgumentException("an import cannot finish before it starts");
    }
    if (outcome == ImportOutcome.Succeeded) {
      if (vehicleCount == null) {
        throw new IllegalArgumentException("a successful import has a vehicle count");
      }
      if (vehicleCount < 0) {
        throw new IllegalArgumentException("a vehicle count cannot be negative");
      }
      if (error != null) {
        throw new IllegalArgumentException("a successful import has no error");
      }
    } else {
      if (vehicleCount != null) {
        throw new IllegalArgumentException("a failed import has no vehicle count");
      }
      error = clean(error);
    }
  }

  private static String clean(String error) {
    var text = error == null ? "" : error.replace("\u0000", "").strip();
    if (text.isEmpty()) {
      throw new IllegalArgumentException("a failed import has an error");
    }
    // By code points, as char_length and the 500 of the column count, never splitting a surrogate pair
    return text.codePoints().limit(MAX_ERROR)
        .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append).toString();
  }
}
