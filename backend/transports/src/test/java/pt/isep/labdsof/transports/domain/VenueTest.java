package pt.isep.labdsof.transports.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class VenueTest {

  private static final Venue QUEIMODROMO = Venue.parse("41.1724,-8.6841");

  @Test
  @DisplayName("US-41 D8 coords are read latitude first")
  void latitudeFirst() {
    assertThat(QUEIMODROMO).isEqualTo(new Venue(-8.6841, 41.1724));
    assertThat(Venue.parse(" 41.1724 , -8.6841 ")).isEqualTo(QUEIMODROMO);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"41.1724", "a,b", "41.1724,-8.6841,0", "91,0", "0,181", "NaN,0"})
  @DisplayName("US-41 D8 coords that are not a latitude and a longitude are refused")
  void badCoords(String coords) {
    assertThatIllegalArgumentException().isThrownBy(() -> Venue.parse(coords));
  }

  @Test
  @DisplayName("US-41 D6 the distance is the great-circle distance in metres")
  void distance() {
    assertThat(QUEIMODROMO.distanceMetres(-8.6841, 41.1724)).isZero();
    assertThat(QUEIMODROMO.distanceMetres(-8.6841, 41.1824)).isEqualTo(1112);
    assertThat(QUEIMODROMO.distanceMetres(-8.6741, 41.1724)).isEqualTo(837);
    assertThat(QUEIMODROMO.distanceMetres(-8.6841, 41.190386)).isCloseTo(2000L, within(1L));
  }
}
