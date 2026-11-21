package pt.isep.labdsof.transports.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The rules of an import (spec US-40 D2, D3). The first broken bus, in the answer's order, rejects the whole import:
 * the previous data stays in use. A valid bus observed more than {@code freshness} before the import is late: left
 * out and counted, and the rest is accepted. Broken wins over late.
 */
public sealed interface BusImport permits BusImport.Accepted, BusImport.Rejected {

  /** Clock drift tolerated between the source and Transports. */
  Duration FUTURE_TOLERANCE = Duration.ofMinutes(1);

  /** The buses to use, in the answer's order, and how many late ones were left out. */
  record Accepted(List<ObservedBus> buses, int late) implements BusImport {
    public Accepted {
      buses = List.copyOf(buses);
    }
  }

  /** Why the import was rejected: {@code entity <position>[ (<id>)]: <reason>}. */
  record Rejected(String reason) implements BusImport {}

  static BusImport of(List<BusReading> readings, Instant retrievedAt, Duration freshness) {
    var buses = new ArrayList<ObservedBus>();
    var seen = new HashSet<String>();
    var oldest = retrievedAt.minus(freshness);
    int late = 0;
    for (var reading : readings) {
      var problem = problem(reading, seen, retrievedAt);
      if (problem != null) {
        var id = blank(reading.externalEntityId()) ? "" : " (" + reading.externalEntityId() + ")";
        return new Rejected("entity " + reading.position() + id + ": " + problem);
      }
      seen.add(reading.externalEntityId());
      if (reading.observedAt().isBefore(oldest)) {
        late++;
      } else {
        buses.add(new ObservedBus(reading.externalEntityId(), reading.dataProvider(), reading.fleetVehicleId(),
            reading.routeId(), reading.longitude(), reading.latitude(), reading.observedAt()));
      }
    }
    return new Accepted(buses, late);
  }

  private static String problem(BusReading r, Set<String> seen, Instant retrievedAt) {
    if (blank(r.externalEntityId())) {
      return "no id";
    }
    if (seen.contains(r.externalEntityId())) {
      return "duplicate id";
    }
    if (blank(r.dataProvider())) {
      return "no dataProvider";
    }
    if (r.longitude() == null || r.latitude() == null) {
      return "no position";
    }
    if (Math.abs(r.longitude()) > 180 || Math.abs(r.latitude()) > 90) {
      return "position out of range";
    }
    if (r.longitude() == 0 && r.latitude() == 0) {
      return "position at 0,0";
    }
    if (blank(r.routeId())) {
      return "no route";
    }
    if (r.observedAt() == null) {
      return "no observation time";
    }
    if (r.observedAt().isAfter(retrievedAt.plus(FUTURE_TOLERANCE))) {
      return "observed in the future";
    }
    return null;
  }

  private static boolean blank(String value) {
    return value == null || value.isBlank();
  }
}
