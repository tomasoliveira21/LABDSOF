package pt.isep.labdsof.transports.application;

import java.time.Instant;
import java.util.List;
import pt.isep.labdsof.transports.domain.ObservedBus;

/** Port: the buses in use, which each valid import replaces (spec US-40 D4). */
public interface BusData {

  /**
   * The buses of a valid import become the buses in use, with the time of the import; a bus that is not among them
   * is no longer in use (its vehicle is kept). Runs in the caller's transaction.
   */
  void replace(List<ObservedBus> buses, Instant retrievedAt);

  /** The buses in use, by their id at the source. */
  List<ObservedBus> inUse();
}
