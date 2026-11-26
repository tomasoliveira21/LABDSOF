package pt.isep.labdsof.transports.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class NewBusRouteTest {

  @Test
  @DisplayName("US-41 D2 the route and the name are trimmed")
  void trimmed() {
    assertThat(NewBusRoute.of(" 500 ", " Hospital S. João ", List.of(), false))
        .isEqualTo(new NewBusRoute("500", "Hospital S. João"));
    assertThat(NewBusRoute.of("500", null, null, false)).isEqualTo(new NewBusRoute("500", null));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "   ", "\t"})
  @DisplayName("US-41 D2 a blank route is refused")
  void blankRoute(String route) {
    assertThatThrownBy(() -> NewBusRoute.of(route, null, List.of(), false))
        .isInstanceOf(InvalidBusRouteException.class).hasMessage("The route is blank.");
  }

  @Test
  @DisplayName("US-41 D2 a missing route is refused as blank")
  void missingRoute() {
    assertThatThrownBy(() -> NewBusRoute.of(null, null, List.of(), false))
        .isInstanceOf(InvalidBusRouteException.class).hasMessage("The route is blank.");
  }

  @Test
  @DisplayName("US-41 D2 a blank name is refused")
  void blankName() {
    assertThatThrownBy(() -> NewBusRoute.of("500", "  ", List.of(), false))
        .isInstanceOf(InvalidBusRouteException.class).hasMessage("The name is blank.");
  }

  @Test
  @DisplayName("US-41 D2 stops arrive with US-37")
  void stopsRefused() {
    assertThatThrownBy(() -> NewBusRoute.of("500", null, List.of(UUID.randomUUID()), false))
        .isInstanceOf(InvalidBusRouteException.class).hasMessage("Stops arrive with US-37.");
  }

  @Test
  @DisplayName("US-41 D2 departures are for shuttles")
  void departuresRefused() {
    assertThatThrownBy(() -> NewBusRoute.of("500", null, List.of(), true))
        .isInstanceOf(InvalidBusRouteException.class).hasMessage("Departures are for shuttles (US-37).");
  }

  @Test
  @DisplayName("US-41 D4 an event holds at most 20 routes")
  void limit() {
    assertThat(NewBusRoute.MAX_PER_EVENT).isEqualTo(20);
  }
}
