package pt.isep.labdsof.transports.application;

import java.util.List;
import java.util.UUID;
import pt.isep.labdsof.transports.domain.BusRoute;

/** Port: an event's transport options (spec US-41 D2, D4); until US-37, only its external bus routes. */
public interface TransportOptions {

  /**
   * What adding a route did. {@code CLOSED} and {@code GONE}: the event's copy, read again under the lock, is closed,
   * or suspended or missing, so an {@code EventChanged} applied after the caller's access check still counts (D3).
   */
  enum Added { ADDED, TAKEN, FULL, CLOSED, GONE }

  /**
   * Stores the route unless the event already has it ({@code TAKEN}) or already holds {@code limit} routes
   * ({@code FULL}). Runs in the caller's transaction and locks the event's copy, so it also holds for requests at the
   * same time. The event's status is read under that lock ({@code CLOSED}, {@code GONE}).
   */
  Added addBusRoute(BusRoute route, int limit);

  /** The event's external bus routes, in the order they were added. */
  List<BusRoute> busRoutes(UUID eventId);
}
