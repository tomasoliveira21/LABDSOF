package pt.isep.labdsof.transports.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * What a member sees of an event's buses (spec US-41 D6, D7): the buses in use on the event's routes, nearest first,
 * each route with how many of its buses are shown, and how old the data is. Computed on each request.
 */
public record BusView(Venue venue, List<RouteSummary> routes, List<ShownBus> buses, Optional<Instant> dataAsOf,
    boolean stale) {

  /** One of the event's routes, in the order it was added; {@code name} may be null. */
  public record RouteSummary(String externalRoute, String name, int busCount) {}

  /** A bus on one of the event's routes, with its distance to the venue in metres. */
  public record ShownBus(ObservedBus bus, long distanceMetres) {}

  private static final Comparator<ShownBus> NEAREST_FIRST = Comparator.comparingLong(ShownBus::distanceMetres)
      .thenComparing(shown -> shown.bus().externalEntityId());

  /**
   * @param lastSucceededAt when the last valid import ended (US-40 D8), empty before any
   * @param freshness D7: older data than this is stale
   */
  public static BusView of(Venue venue, List<BusRoute> routes, List<ObservedBus> inUse,
      Optional<Instant> lastSucceededAt, Instant now, Duration freshness) {
    var numbers = routes.stream().map(BusRoute::externalRoute).collect(Collectors.toSet());
    var shown = inUse.stream().filter(bus -> numbers.contains(bus.routeId()))
        .map(bus -> new ShownBus(bus, venue.distanceMetres(bus.longitude(), bus.latitude())))
        .sorted(NEAREST_FIRST).toList();
    Map<String, Long> perRoute = shown.stream()
        .collect(Collectors.groupingBy(s -> s.bus().routeId(), Collectors.counting()));
    var summaries = routes.stream().map(route -> new RouteSummary(route.externalRoute(), route.name(),
        perRoute.getOrDefault(route.externalRoute(), 0L).intValue())).toList();
    var stale = lastSucceededAt.map(at -> Duration.between(at, now).compareTo(freshness) > 0).orElse(true);
    return new BusView(venue, summaries, shown, lastSucceededAt, stale);
  }
}
