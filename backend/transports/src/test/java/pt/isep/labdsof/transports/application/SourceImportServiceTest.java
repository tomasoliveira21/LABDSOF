package pt.isep.labdsof.transports.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionOperations;
import pt.isep.labdsof.transports.domain.BusReading;
import pt.isep.labdsof.transports.domain.ImportOutcome;
import pt.isep.labdsof.transports.domain.ObservedBus;
import pt.isep.labdsof.transports.domain.SourceImport;

class SourceImportServiceTest {

  private static final Instant T0 = Instant.parse("2027-05-08T23:14:05Z");
  /** The time the answer arrives: the second reading of the stepping clock. */
  private static final Instant ARRIVED = T0.plusMillis(250);

  /** A clock that moves 250 ms each time it is read. */
  private static final class SteppingClock extends Clock {
    private Instant now = T0;

    @Override
    public ZoneId getZone() {
      return ZoneId.of("UTC");
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      var current = now;
      now = now.plus(Duration.ofMillis(250));
      return current;
    }
  }

  private final RecordedImports imports = new RecordedImports();
  private final InMemoryBusData buses = new InMemoryBusData();
  private final List<ImportRun> observed = new ArrayList<>();

  private SourceImportService service(SourceReading reading, BusData data) {
    return new SourceImportService(() -> reading, imports, data, observed::add,
        TransactionOperations.withoutTransaction(), new SteppingClock(), Duration.ofMinutes(5));
  }

  private ImportRun run(SourceReading reading) {
    return service(reading, buses).runOnce();
  }

  private static BusReading bus(int position, String id, Instant observedAt) {
    return new BusReading(position, id, "STCP", "1126", "205", -8.688, 41.168, observedAt);
  }

  private static ObservedBus observed(String id, Instant observedAt) {
    return new ObservedBus(id, "STCP", "1126", "205", -8.688, 41.168, observedAt);
  }

  @Test
  @DisplayName("US-40 AC-1 an answered import is stored with the time it arrived and recorded Succeeded with the number "
      + "of buses")
  void answeredImportIsStored() {
    var seen = T0.minusSeconds(57);

    var run = run(SourceReading.answered(List.of(bus(0, "b", seen), bus(1, "a", seen)), 1));

    var expected = new SourceImport(T0, ARRIVED, ImportOutcome.Succeeded, 2, null);
    assertThat(run).isEqualTo(new ImportRun(expected, 0, 1));
    assertThat(imports.recorded).containsExactly(expected);
    assertThat(buses.inUse()).containsExactly(observed("a", seen), observed("b", seen));
    assertThat(buses.retrievedAt()).isEqualTo(ARRIVED);
  }

  @Test
  @DisplayName("US-40 AC-2 a rejected import stores nothing and is recorded Invalid with the reason")
  void rejectedImportStoresNothing() {
    buses.replace(List.of(observed("previous", T0)), T0);
    var broken = new BusReading(0, "x", "STCP", null, null, -8.6, 41.1, T0);

    var run = run(SourceReading.answered(List.of(broken), 2));

    var expected = new SourceImport(T0, ARRIVED, ImportOutcome.Invalid, null, "entity 0 (x): no route");
    assertThat(run).isEqualTo(new ImportRun(expected, 0, 2));
    assertThat(imports.recorded).containsExactly(expected);
    assertThat(buses.inUse()).containsExactly(observed("previous", T0));
  }

  @Test
  @DisplayName("US-40 AC-3 late buses are left out and counted in the run")
  void lateBusesAreCounted() {
    var fresh = T0.minusSeconds(57);
    var old = T0.minus(Duration.ofMinutes(6));

    var run = run(SourceReading.answered(List.of(bus(0, "fresh", fresh), bus(1, "old", old)), 1));

    assertThat(run.late()).isEqualTo(1);
    assertThat(run.sourceImport().vehicleCount()).isEqualTo(1);
    assertThat(buses.inUse()).containsExactly(observed("fresh", fresh));
  }

  @Test
  @DisplayName("US-40 AC-6 an unavailable source is recorded with its reason and stores nothing")
  void unavailableSourceIsRecorded() {
    buses.replace(List.of(observed("previous", T0)), T0);

    var run = run(SourceReading.unavailable("HTTP 503", 3));

    var expected = new SourceImport(T0, ARRIVED, ImportOutcome.Unavailable, null, "HTTP 503");
    assertThat(run).isEqualTo(new ImportRun(expected, 0, 3));
    assertThat(imports.recorded).containsExactly(expected);
    assertThat(buses.inUse()).containsExactly(observed("previous", T0));
  }

  @Test
  @DisplayName("193 AC-6 an answer that is not a list of entities is recorded Invalid with its reason")
  void invalidAnswerIsRecorded() {
    var run = run(SourceReading.invalid("not a JSON array", 1));

    var expected = new SourceImport(T0, ARRIVED, ImportOutcome.Invalid, null, "not a JSON array");
    assertThat(run).isEqualTo(new ImportRun(expected, 0, 1));
    assertThat(imports.recorded).containsExactly(expected);
  }

  @Test
  @DisplayName("US-40 AC-6 each run is passed to the observer")
  void eachRunIsObserved() {
    var first = run(SourceReading.unavailable("HTTP 503", 3));
    var second = run(SourceReading.answered(List.of(), 1));

    assertThat(observed).containsExactly(first, second);
  }

  @Test
  @DisplayName("US-40 AC-2 a storing failure records nothing and reaches neither the buses nor the observer")
  void storingFailureRecordsNothing() {
    var failing = new InMemoryBusData(new IllegalStateException("database down"));
    var service = service(SourceReading.answered(List.of(bus(0, "a", T0.minusSeconds(57))), 1), failing);

    assertThatThrownBy(service::runOnce).isInstanceOf(IllegalStateException.class).hasMessage("database down");
    assertThat(imports.recorded).isEmpty();
    assertThat(failing.inUse()).isEmpty();
    assertThat(observed).isEmpty();
  }

  @Test
  @DisplayName("US-40 AC-6 the readings carry their buses or reason and the number of attempts")
  void readingsCarryTheirFields() {
    assertThat(SourceReading.answered(List.of(), 2)).isEqualTo(new SourceReading.Answered(List.of(), 2));
    assertThat(SourceReading.unavailable("x", 3)).isEqualTo(new SourceReading.Unavailable("x", 3));
    assertThat(SourceReading.invalid("y", 1)).isEqualTo(new SourceReading.Invalid("y", 1));
  }
}
