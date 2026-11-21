package pt.isep.labdsof.events.domain;

import java.time.LocalDate;
import java.util.Objects;

/** Local dates of an event; a single day is allowed (spec D4). */
public record DateRange(LocalDate start, LocalDate end) {

  public DateRange {
    Objects.requireNonNull(start, "start");
    Objects.requireNonNull(end, "end");
    if (end.isBefore(start)) {
      throw new IllegalArgumentException("the event cannot end before it starts");
    }
  }
}
