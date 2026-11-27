package pt.isep.labdsof.transports.domain;

import java.util.List;
import java.util.UUID;

/** What a request asks to add as a bus route, once checked (spec US-41 D2, D4): trimmed, {@code name} may be null. */
public record NewBusRoute(String externalRoute, String name) {

  /** D4: the routes an event may hold. */
  public static final int MAX_PER_EVENT = 20;

  /**
   * The contract bounds the lengths; this adds what it cannot say: blank text, control characters, stops (US-37) and
   * departures (shuttles).
   *
   * @throws InvalidBusRouteException with the Problem's detail
   */
  public static NewBusRoute of(String externalRoute, String name, List<UUID> stopPoiIds, boolean hasDepartures) {
    if (externalRoute == null || externalRoute.isBlank()) {
      throw new InvalidBusRouteException("The route is blank.");
    }
    if (name != null && name.isBlank()) {
      throw new InvalidBusRouteException("The name is blank.");
    }
    // PostgreSQL cannot store NUL (a 500 otherwise), and no route number or name needs a control character
    if (externalRoute.chars().anyMatch(Character::isISOControl)) {
      throw new InvalidBusRouteException("The route has a control character.");
    }
    if (name != null && name.chars().anyMatch(Character::isISOControl)) {
      throw new InvalidBusRouteException("The name has a control character.");
    }
    if (stopPoiIds != null && !stopPoiIds.isEmpty()) {
      throw new InvalidBusRouteException("Stops arrive with US-37.");
    }
    if (hasDepartures) {
      throw new InvalidBusRouteException("Departures are for shuttles (US-37).");
    }
    return new NewBusRoute(externalRoute.strip(), name == null ? null : name.strip());
  }
}
