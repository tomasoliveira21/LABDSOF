package pt.isep.labdsof.transports;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import pt.isep.labdsof.transports.application.BusData;
import pt.isep.labdsof.transports.application.SourceImports;
import pt.isep.labdsof.transports.domain.ImportOutcome;
import pt.isep.labdsof.transports.domain.ObservedBus;
import pt.isep.labdsof.transports.domain.SourceImport;

/** The buses in use, replaced by each valid import (spec US-40 D4), and the time of the last valid import. */
class BusDataIT extends IntegrationTest {

  private static final Instant FIRST = Instant.parse("2027-05-08T23:14:05Z");
  private static final Instant SECOND = FIRST.plusSeconds(30);

  @Autowired BusData buses;
  @Autowired SourceImports imports;

  private static ObservedBus bus(String id, String fleet, String route, double longitude, Instant observedAt) {
    return new ObservedBus(id, "STCP", fleet, route, longitude, 41.168, observedAt);
  }

  private long vehicles(String id) {
    return jdbc.sql("SELECT count(*) FROM bus_vehicle WHERE data_provider = 'STCP' AND external_entity_id = :id")
        .param("id", id).query(Long.class).single();
  }

  @Test
  @DisplayName("US-40 AC-1 the buses are stored with their route, position, observation time and the time of the import")
  void busesAreStored() {
    var a = bus("stcp:a", "3301", "205", -8.688, FIRST.minusSeconds(57));
    var b = bus("stcp:b", "3302", "500", -8.672, FIRST.minusSeconds(61));

    buses.replace(List.of(b, a), FIRST);

    assertThat(buses.inUse()).containsExactly(a, b);
    assertThat(jdbc.sql("SELECT DISTINCT retrieved_at FROM bus_observation").query(Timestamp.class).list())
        .containsExactly(Timestamp.from(FIRST));
  }

  @Test
  @DisplayName("US-40 AC-4 a bus that is not in the next import is no longer in use, and its vehicle row stays")
  void absentBusIsNoLongerInUse() {
    var a = bus("stcp:gone", "3301", "205", -8.688, FIRST.minusSeconds(57));
    var b = bus("stcp:stays", "3302", "500", -8.672, FIRST.minusSeconds(57));
    buses.replace(List.of(a, b), FIRST);

    buses.replace(List.of(b), SECOND);

    assertThat(buses.inUse()).containsExactly(b);
    assertThat(vehicles("stcp:gone")).isOne();
  }

  @Test
  @DisplayName("US-40 AC-4 the same bus in two imports is one vehicle, its position updated")
  void sameBusIsUpdated() {
    buses.replace(List.of(bus("stcp:moving", "3301", "205", -8.688, FIRST.minusSeconds(57))), FIRST);
    var moved = bus("stcp:moving", "3301", "205", -8.690, SECOND.minusSeconds(40));

    buses.replace(List.of(moved), SECOND);

    assertThat(buses.inUse()).containsExactly(moved);
    assertThat(vehicles("stcp:moving")).isOne();
    assertThat(jdbc.sql("SELECT retrieved_at FROM bus_observation").query(Timestamp.class).single())
        .isEqualTo(Timestamp.from(SECOND));
  }

  @Test
  @DisplayName("US-40 AC-4 an empty import leaves no bus in use")
  void emptyImportLeavesNoBus() {
    buses.replace(List.of(bus("stcp:x", "3301", "205", -8.688, FIRST.minusSeconds(57))), FIRST);

    buses.replace(List.of(), SECOND);

    assertThat(buses.inUse()).isEmpty();
  }

  @Test
  @DisplayName("US-40 AC-1 a bus without fleetVehicleId is stored without it")
  void busWithoutFleetNumber() {
    var noFleet = bus("stcp:nofleet", null, "205", -8.688, FIRST.minusSeconds(57));

    buses.replace(List.of(noFleet), FIRST);

    assertThat(buses.inUse()).containsExactly(noFleet);
  }

  @Test
  @DisplayName("US-40 AC-4 a bus whose fleet number changes between imports keeps one vehicle row, updated")
  void fleetNumberChange() {
    buses.replace(List.of(bus("stcp:renumbered", "3301", "205", -8.688, FIRST.minusSeconds(57))), FIRST);
    var renumbered = bus("stcp:renumbered", "4401", "205", -8.688, SECOND.minusSeconds(57));

    buses.replace(List.of(renumbered), SECOND);

    assertThat(buses.inUse()).containsExactly(renumbered);
    assertThat(vehicles("stcp:renumbered")).isOne();
  }

  @Test
  @DisplayName("US-40 AC-1 an import of 1000 buses is stored in under 5 seconds")
  void thousandBuses() {
    var many = IntStream.range(0, 1000)
        .mapToObj(i -> bus("stcp:bulk-%04d".formatted(i), String.valueOf(5000 + i), "205", -8.6 - i / 10000.0,
            FIRST.minusSeconds(57)))
        .toList();

    var started = System.nanoTime();
    buses.replace(many, FIRST);
    var elapsed = Duration.ofNanos(System.nanoTime() - started);

    assertThat(buses.inUse()).hasSize(1000).first().isEqualTo(many.getFirst());
    assertThat(elapsed).isLessThan(Duration.ofSeconds(5));
  }

  @Test
  @DisplayName("US-40 AC-7 the time of the last successful import is the latest Succeeded row's end")
  void lastSucceededAt() {
    // Times in the past, removed afterwards: other tests count the imports since they started
    var first = Instant.parse("2026-01-02T10:00:00Z");
    var second = first.plusSeconds(30);
    jdbc.sql("DELETE FROM source_import").update();
    try {
      assertThat(imports.lastSucceededAt()).isEmpty();

      imports.record(new SourceImport(first, first.plusMillis(300), ImportOutcome.Succeeded, 4, null));
      imports.record(new SourceImport(second, second.plusMillis(300), ImportOutcome.Invalid, null, "entity 0: no id"));

      assertThat(imports.lastSucceededAt()).contains(first.plusMillis(300));
    } finally {
      jdbc.sql("DELETE FROM source_import").update();
    }
  }
}
