package pt.isep.labdsof.transports.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import pt.isep.labdsof.transports.domain.BusView.RouteSummary;
import pt.isep.labdsof.transports.domain.BusView.ShownBus;

class BusViewTest {

  private static final Venue VENUE = new Venue(-8.6841, 41.1724);
  private static final Instant NOW = Instant.parse("2027-05-08T23:14:05Z");
  private static final Duration FRESHNESS = Duration.ofMinutes(5);
  private static final UUID EVENT = UUID.fromString("0b6f3c1e-5a3b-4f0e-9d0e-6a1b2c3d4e5f");
  private static final Optional<Instant> FRESH = Optional.of(NOW.minusSeconds(20));

  private static ObservedBus bus(String id, String fleet, String route, double longitude, double latitude) {
    return new ObservedBus(id, "STCP", fleet, route, longitude, latitude, NOW.minusSeconds(57));
  }

  private static BusRoute route(String number, String name) {
    return new BusRoute(UUID.randomUUID(), EVENT, number, name, NOW, UUID.randomUUID());
  }

  private static List<String> shownIds(BusView view) {
    return view.buses().stream().map(shown -> shown.bus().externalEntityId()).toList();
  }

  @Test
  @DisplayName("US-41 AC-2 only the buses on the event's routes are shown")
  void onlyEventRoutes() {
    var view = BusView.of(VENUE, List.of(route("500", null), route("502", null)),
        List.of(bus("a", "1", "500", -8.6841, 41.1734), bus("b", "2", "200", -8.6841, 41.1724),
            bus("c", "3", "502", -8.6841, 41.1824)),
        FRESH, NOW, FRESHNESS);

    assertThat(shownIds(view)).containsExactly("a", "c");
  }

  @Test
  @DisplayName("US-41 D6 routes are matched exactly")
  void exactMatch() {
    var view = BusView.of(VENUE, List.of(route("500", null), route("zc", null)),
        List.of(bus("a", "1", "50", -8.6841, 41.1734), bus("b", "2", "ZC", -8.6841, 41.1734)),
        FRESH, NOW, FRESHNESS);

    assertThat(view.buses()).isEmpty();
  }

  @Test
  @DisplayName("US-41 D6 nearest first, ties by vehicle id, with the distance to the venue")
  void nearestFirst() {
    var view = BusView.of(VENUE, List.of(route("500", null)),
        List.of(bus("far", "1", "500", -8.6841, 41.1824), bus("b", "2", "500", -8.6841, 41.1734),
            bus("a", "3", "500", -8.6841, 41.1734)),
        FRESH, NOW, FRESHNESS);

    assertThat(shownIds(view)).containsExactly("a", "b", "far");
    assertThat(view.buses()).extracting(ShownBus::distanceMetres).containsExactly(111L, 111L, 1112L);
    assertThat(view.venue()).isEqualTo(VENUE);
  }

  @Test
  @DisplayName("US-41 D6 each route counts its buses, in the order the routes were added")
  void routeCounts() {
    var view = BusView.of(VENUE, List.of(route("500", null), route("502", "Hospital S. João")),
        List.of(bus("a", "1", "500", -8.6841, 41.1734), bus("b", "2", "500", -8.6841, 41.1744)),
        FRESH, NOW, FRESHNESS);

    assertThat(view.routes()).containsExactly(new RouteSummary("500", null, 2),
        new RouteSummary("502", "Hospital S. João", 0));
  }

  @Test
  @DisplayName("US-41 D7 stale without a valid import, or when it is older than 5 min")
  void stale() {
    var routes = List.of(route("500", null));

    var never = BusView.of(VENUE, routes, List.of(), Optional.empty(), NOW, FRESHNESS);
    assertThat(never.stale()).isTrue();
    assertThat(never.dataAsOf()).isEmpty();

    assertThat(BusView.of(VENUE, routes, List.of(), Optional.of(NOW.minusSeconds(299)), NOW, FRESHNESS).stale())
        .isFalse();
    assertThat(BusView.of(VENUE, routes, List.of(), Optional.of(NOW.minusSeconds(300)), NOW, FRESHNESS).stale())
        .isFalse();
    var late = BusView.of(VENUE, routes, List.of(), Optional.of(NOW.minusSeconds(301)), NOW, FRESHNESS);
    assertThat(late.stale()).isTrue();
    assertThat(late.dataAsOf()).contains(NOW.minusSeconds(301));
  }

  @Test
  @DisplayName("US-41 D6 no routes give no buses")
  void noRoutes() {
    var view = BusView.of(VENUE, List.of(), List.of(bus("a", "1", "500", -8.6841, 41.1734)), FRESH, NOW, FRESHNESS);

    assertThat(view.routes()).isEmpty();
    assertThat(view.buses()).isEmpty();
  }
}
