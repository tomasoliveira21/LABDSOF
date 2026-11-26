package pt.isep.labdsof.transports.application;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import pt.isep.labdsof.transports.domain.ObservedBus;

/** In-memory {@link BusData}: the last replaced set, optionally failing on replace. */
public final class InMemoryBusData implements BusData {

  private List<ObservedBus> inUse = List.of();
  private Instant retrievedAt;
  private final RuntimeException failure;

  public InMemoryBusData() {
    this(null);
  }

  public InMemoryBusData(RuntimeException failure) {
    this.failure = failure;
  }

  @Override
  public void replace(List<ObservedBus> buses, Instant retrievedAt) {
    if (failure != null) {
      throw failure;
    }
    this.inUse = buses.stream().sorted(Comparator.comparing(ObservedBus::externalEntityId)).toList();
    this.retrievedAt = retrievedAt;
  }

  @Override
  public List<ObservedBus> inUse() {
    return inUse;
  }

  public Instant retrievedAt() {
    return retrievedAt;
  }
}
