package pt.isep.labdsof.transports.application;

import java.util.List;
import pt.isep.labdsof.transports.domain.BusReading;

/**
 * What the source answered to one run (spec US-40 D5, D7), and after how many calls: the buses it sent, the reason it
 * could not be read (timeout, connection error, HTTP error, circuit open), or why its answer is not a list of NGSI
 * entities.
 */
public sealed interface SourceReading {

  int attempts();

  record Answered(List<BusReading> buses, int attempts) implements SourceReading {
    public Answered {
      buses = List.copyOf(buses);
    }
  }

  record Unavailable(String reason, int attempts) implements SourceReading {}

  record Invalid(String reason, int attempts) implements SourceReading {}

  static SourceReading answered(List<BusReading> buses, int attempts) {
    return new Answered(buses, attempts);
  }

  static SourceReading unavailable(String reason, int attempts) {
    return new Unavailable(reason, attempts);
  }

  static SourceReading invalid(String reason, int attempts) {
    return new Invalid(reason, attempts);
  }
}
