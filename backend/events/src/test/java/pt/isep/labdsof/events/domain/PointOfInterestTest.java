package pt.isep.labdsof.events.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** A point on a map version: US-19 only copies them, US-20 places and moves them. */
class PointOfInterestTest {

  private static final UUID EVENT = UUID.randomUUID();

  private static PointOfInterest point(String name, boolean deleted) {
    return new PointOfInterest(UUID.randomUUID(), EVENT, 2, PoiType.Wc, name, new MapPosition(0.25, 0.75),
        deleted, 3);
  }

  @Test
  @DisplayName("US-19 AC-1 a copied point keeps the type, the name and the position, with a new id and version 1")
  void aCopyKeepsWhatMatters() {
    var original = point("WC norte", false);
    var id = UUID.randomUUID();

    var copy = original.copiedTo(3, id);

    assertThat(copy.poiId()).isEqualTo(id).isNotEqualTo(original.poiId());
    assertThat(copy.mapVersion()).isEqualTo(3);
    assertThat(copy.version()).isEqualTo(1);
    assertThat(copy.deleted()).isFalse();
    assertThat(copy.eventId()).isEqualTo(original.eventId());
    assertThat(copy.type()).isEqualTo(original.type());
    assertThat(copy.name()).isEqualTo(original.name());
    assertThat(copy.position()).isEqualTo(original.position());
  }

  @Test
  @DisplayName("US-19 AC-1 the name of a point is trimmed, as the table's check sees it")
  void theNameIsTrimmed() {
    assertThat(point("  WC sul  ", false).name()).isEqualTo("WC sul");
  }

  @ParameterizedTest(name = "[{index}]")
  @ValueSource(strings = {"", "   "})
  @DisplayName("US-19 AC-1 a point always has a name of 1 to 100 characters")
  void theNameIsNeverBlank(String name) {
    assertThatThrownBy(() -> point(name, false))
        .isInstanceOf(IllegalArgumentException.class).hasMessage("a point name has 1 to 100 characters");
  }

  @Test
  @DisplayName("US-19 AC-1 a point name of 100 characters is accepted and one of 101 is not")
  void theNameLengthIsBounded() {
    assertThat(point("x".repeat(100), false).name()).hasSize(100);
    assertThatThrownBy(() -> point("x".repeat(101), false)).isInstanceOf(IllegalArgumentException.class);
  }

  @ParameterizedTest(name = "mapVersion {0}, version {1}")
  @CsvSource({"0, 1", "-1, 1", "2, 0", "2, -1"})
  @DisplayName("US-19 AC-1 both the map version and the point's own version start at 1")
  void versionsStartAtOne(int mapVersion, long version) {
    assertThatThrownBy(() -> new PointOfInterest(UUID.randomUUID(), EVENT, mapVersion, PoiType.Other, "P",
        new MapPosition(0, 0), false, version)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("US-19 AC-1 a point always names its id, its event, its type, its name and its position")
  void theRequiredValuesAreRequired() {
    assertThatThrownBy(() -> new PointOfInterest(null, EVENT, 1, PoiType.Other, "P", new MapPosition(0, 0),
        false, 1)).isInstanceOf(NullPointerException.class).hasMessage("poiId");
    assertThatThrownBy(() -> new PointOfInterest(UUID.randomUUID(), null, 1, PoiType.Other, "P",
        new MapPosition(0, 0), false, 1)).isInstanceOf(NullPointerException.class).hasMessage("eventId");
    assertThatThrownBy(() -> new PointOfInterest(UUID.randomUUID(), EVENT, 1, null, "P", new MapPosition(0, 0),
        false, 1)).isInstanceOf(NullPointerException.class).hasMessage("type");
    assertThatThrownBy(() -> new PointOfInterest(UUID.randomUUID(), EVENT, 1, PoiType.Other, "P", null,
        false, 1)).isInstanceOf(NullPointerException.class).hasMessage("position");
    assertThatThrownBy(() -> new PointOfInterest(UUID.randomUUID(), EVENT, 1, PoiType.Other, null,
        new MapPosition(0, 0), false, 1)).isInstanceOf(NullPointerException.class).hasMessage("name");
  }

  @ParameterizedTest(name = "({0}, {1})")
  @CsvSource({"0, 0", "1, 1", "0.5, 0.25"})
  @DisplayName("US-19 AC-1 a position inside the map is accepted, bounds included")
  void positionsInsideTheMapAreAccepted(double x, double y) {
    assertThat(new MapPosition(x, y)).isEqualTo(new MapPosition(x, y));
  }

  @ParameterizedTest(name = "({0}, {1})")
  @CsvSource({"-0.01, 0.5", "1.01, 0.5", "0.5, -0.01", "0.5, 1.01", "NaN, 0.5", "0.5, NaN"})
  @DisplayName("US-19 AC-1 a position outside the map is a broken row, never a point")
  void positionsOutsideTheMapAreRefused(double x, double y) {
    assertThatThrownBy(() -> new MapPosition(x, y))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("between 0 and 1");
  }
}
