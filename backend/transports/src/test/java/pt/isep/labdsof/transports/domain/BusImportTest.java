package pt.isep.labdsof.transports.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import pt.isep.labdsof.transports.domain.BusImport.Accepted;
import pt.isep.labdsof.transports.domain.BusImport.Rejected;

class BusImportTest {

  private static final Instant AT = Instant.parse("2027-05-08T23:14:05Z");
  private static final Duration FRESHNESS = Duration.ofMinutes(5);
  private static final String ID = "urn:ngsi-ld:Vehicle:porto:stcp:bus:1126";

  /** A valid reading at {@code position}, observed 57 s before the import, as the recordings are. */
  private static BusReading bus(int position, String id) {
    return new BusReading(position, id, "STCP", "1126", "205", -8.688, 41.168, AT.minusSeconds(57));
  }

  private static ObservedBus observed(String id) {
    return new ObservedBus(id, "STCP", "1126", "205", -8.688, 41.168, AT.minusSeconds(57));
  }

  private static BusImport check(BusReading... readings) {
    return BusImport.of(List.of(readings), AT, FRESHNESS);
  }

  /** The reading with one field changed. */
  private static BusReading with(BusReading r, String field, Object value) {
    return new BusReading(r.position(),
        field.equals("id") ? (String) value : r.externalEntityId(),
        field.equals("dataProvider") ? (String) value : r.dataProvider(),
        field.equals("fleetVehicleId") ? (String) value : r.fleetVehicleId(),
        field.equals("routeId") ? (String) value : r.routeId(),
        field.equals("longitude") ? (Double) value : r.longitude(),
        field.equals("latitude") ? (Double) value : r.latitude(),
        field.equals("observedAt") ? (Instant) value : r.observedAt());
  }

  @Test
  @DisplayName("US-40 AC-1 valid readings become the accepted buses, in the answer's order")
  void validReadingsAreAccepted() {
    assertThat(check(bus(0, "a"), bus(1, "b"))).isEqualTo(new Accepted(List.of(observed("a"), observed("b")), 0));
  }

  @Test
  @DisplayName("US-40 AC-1 no readings is an accepted import of no bus")
  void noReadingsIsAnEmptyImport() {
    assertThat(check()).isEqualTo(new Accepted(List.of(), 0));
  }

  @Test
  @DisplayName("US-40 AC-1 a bus without fleetVehicleId is accepted")
  void fleetVehicleIdIsOptional() {
    var accepted = (Accepted) check(with(bus(0, ID), "fleetVehicleId", null));
    assertThat(accepted.buses()).singleElement().satisfies(b -> assertThat(b.fleetVehicleId()).isNull());
  }

  private static Stream<Arguments> brokenBuses() {
    UnaryOperator<BusReading> id = r -> r;
    return Stream.of(
        Arguments.of("no id", (UnaryOperator<BusReading>) r -> with(r, "id", null), "entity 3: no id"),
        Arguments.of("blank id", (UnaryOperator<BusReading>) r -> with(r, "id", "  "), "entity 3: no id"),
        Arguments.of("no dataProvider", (UnaryOperator<BusReading>) r -> with(r, "dataProvider", null),
            "entity 3 (" + ID + "): no dataProvider"),
        Arguments.of("blank dataProvider", (UnaryOperator<BusReading>) r -> with(r, "dataProvider", " "),
            "entity 3 (" + ID + "): no dataProvider"),
        Arguments.of("no longitude", (UnaryOperator<BusReading>) r -> with(r, "longitude", null),
            "entity 3 (" + ID + "): no position"),
        Arguments.of("no latitude", (UnaryOperator<BusReading>) r -> with(r, "latitude", null),
            "entity 3 (" + ID + "): no position"),
        Arguments.of("longitude out of range", (UnaryOperator<BusReading>) r -> with(r, "longitude", 181.0),
            "entity 3 (" + ID + "): position out of range"),
        Arguments.of("latitude out of range", (UnaryOperator<BusReading>) r -> with(r, "latitude", -91.0),
            "entity 3 (" + ID + "): position out of range"),
        Arguments.of("0,0", (UnaryOperator<BusReading>) r -> with(with(r, "longitude", 0.0), "latitude", 0.0),
            "entity 3 (" + ID + "): position at 0,0"),
        Arguments.of("no route", (UnaryOperator<BusReading>) r -> with(r, "routeId", null),
            "entity 3 (" + ID + "): no route"),
        Arguments.of("blank route", (UnaryOperator<BusReading>) r -> with(r, "routeId", ""),
            "entity 3 (" + ID + "): no route"),
        Arguments.of("no observation time", (UnaryOperator<BusReading>) r -> with(r, "observedAt", null),
            "entity 3 (" + ID + "): no observation time"),
        Arguments.of("observed in the future",
            (UnaryOperator<BusReading>) r -> with(r, "observedAt", AT.plusSeconds(61)),
            "entity 3 (" + ID + "): observed in the future"),
        Arguments.of("valid (control)", id, null));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("brokenBuses")
  @DisplayName("US-40 AC-2 the first broken bus rejects the whole import, named by its position and id")
  void aBrokenBusRejectsTheImport(String name, UnaryOperator<BusReading> breakIt, String reason) {
    var result = check(bus(0, "a"), bus(1, "b"), bus(2, "c"), breakIt.apply(bus(3, ID)), bus(4, "e"));

    if (reason == null) {
      assertThat(result).isInstanceOf(Accepted.class);
    } else {
      assertThat(result).isEqualTo(new Rejected(reason));
    }
  }

  @Test
  @DisplayName("US-40 AC-2 the same id twice in one answer rejects the import at the second one")
  void duplicateIdRejects() {
    assertThat(check(bus(0, "a"), bus(1, ID), bus(2, ID))).isEqualTo(new Rejected("entity 2 (" + ID + "): duplicate id"));
  }

  @Test
  @DisplayName("US-40 AC-2 a broken bus after valid ones still rejects the import, and the first broken one is named")
  void theFirstBrokenBusIsNamed() {
    assertThat(check(bus(0, "a"), with(bus(1, "b"), "routeId", null), with(bus(2, "c"), "dataProvider", null)))
        .isEqualTo(new Rejected("entity 1 (b): no route"));
  }

  @ParameterizedTest(name = "{0},{1} accepted: {2}")
  @CsvSource({"180,90,true", "-180,-90,true", "180.0001,41,false", "-180.0001,41,false", "-8.6,90.0001,false",
      "-8.6,-90.0001,false", "0,41.1,true", "-8.6,0,true", "0,0,false"})
  @DisplayName("US-40 AC-2 the boundaries of a position: ±180 and ±90 are accepted, beyond them and 0,0 are not")
  void positionBoundaries(double longitude, double latitude, boolean accepted) {
    var result = check(with(with(bus(0, ID), "longitude", longitude), "latitude", latitude));
    assertThat(result instanceof Accepted).isEqualTo(accepted);
  }

  @ParameterizedTest(name = "{0} s after the import accepted: {1}")
  @CsvSource({"59,true", "60,true", "61,false"})
  @DisplayName("US-40 AC-2 observed up to 1 min after the import is accepted, later is not")
  void futureTolerance(long seconds, boolean accepted) {
    var result = check(with(bus(0, ID), "observedAt", AT.plusSeconds(seconds)));
    assertThat(result instanceof Accepted).isEqualTo(accepted);
  }

  @Test
  @DisplayName("US-40 AC-3 a bus observed more than 5 min before the import is dropped and counted, at exactly 5 min "
      + "it is kept")
  void lateBusesAreDropped() {
    var late = with(bus(1, "late"), "observedAt", AT.minus(FRESHNESS).minusSeconds(1));
    var limit = with(bus(2, "limit"), "observedAt", AT.minus(FRESHNESS));

    var accepted = (Accepted) check(bus(0, "a"), late, limit);

    assertThat(accepted.buses()).extracting(ObservedBus::externalEntityId).containsExactly("a", "limit");
    assertThat(accepted.late()).isEqualTo(1);
  }

  @Test
  @DisplayName("US-40 D3 an answer whose buses are all late is rejected with how many and the newest observation, "
      + "and an answer of no bus is accepted with none")
  void everyBusLate() {
    var tenMinutes = with(bus(0, "a"), "observedAt", AT.minus(Duration.ofMinutes(10)));
    var eightMinutes = with(bus(1, "b"), "observedAt", AT.minus(Duration.ofMinutes(8)));
    var twelveMinutes = with(bus(2, "c"), "observedAt", AT.minus(Duration.ofMinutes(12)));

    assertThat(check(tenMinutes, eightMinutes, twelveMinutes))
        .isEqualTo(new Rejected("every bus is late (3, the newest observed at 2027-05-08T23:06:05Z)"));
    assertThat(check()).isEqualTo(new Accepted(List.of(), 0));
  }

  @Test
  @DisplayName("US-40 AC-3 a late bus that is also broken rejects the import")
  void brokenWinsOverLate() {
    var lateAndBroken = with(with(bus(0, ID), "observedAt", AT.minus(Duration.ofMinutes(10))), "routeId", null);
    assertThat(check(lateAndBroken)).isEqualTo(new Rejected("entity 0 (" + ID + "): no route"));
  }
}
