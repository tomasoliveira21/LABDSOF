package pt.isep.labdsof.transports.adapters.in.scheduling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.MDC;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.transaction.support.TransactionOperations;
import pt.isep.labdsof.transports.Correlation;
import pt.isep.labdsof.transports.application.BusData;
import pt.isep.labdsof.transports.application.InMemoryBusData;
import pt.isep.labdsof.transports.application.RecordedImports;
import pt.isep.labdsof.transports.application.SourceImportService;
import pt.isep.labdsof.transports.application.SourceReading;
import pt.isep.labdsof.transports.application.TransportSource;
import pt.isep.labdsof.transports.domain.BusReading;

@ExtendWith(OutputCaptureExtension.class)
class SourceImportJobTest {

  private final List<String> seen = new ArrayList<>();

  private static SourceImportJob job(TransportSource source, BusData buses) {
    return new SourceImportJob(new SourceImportService(source, new RecordedImports(), buses, run -> { },
        TransactionOperations.withoutTransaction(), Clock.systemUTC(), Duration.ofMinutes(5)));
  }

  private static SourceImportJob job(TransportSource source) {
    return job(source, new InMemoryBusData());
  }

  /** Buses observed just now, then {@code late} observed 10 min ago. */
  private static SourceReading buses(int fresh, int late, int attempts) {
    var readings = new ArrayList<BusReading>();
    for (int i = 0; i < fresh + late; i++) {
      var observedAt = i < fresh ? Instant.now() : Instant.now().minus(Duration.ofMinutes(10));
      readings.add(new BusReading(i, "stcp:" + i, "STCP", null, "205", -8.6, 41.1, observedAt));
    }
    return SourceReading.answered(readings, attempts);
  }

  @Test
  @DisplayName("193 AC-7 each run carries its own correlation id, removed after the run")
  void eachRunCarriesItsOwnCorrelationId() {
    var job = job(() -> {
      seen.add(MDC.get(Correlation.MDC_KEY));
      return buses(2, 0, 1);
    });

    job.run();
    job.run();

    assertThat(seen).hasSize(2).doesNotContainNull();
    assertThat(seen.get(0)).isNotEqualTo(seen.get(1));
    assertThat(MDC.get(Correlation.MDC_KEY)).isNull();
  }

  @Test
  @DisplayName("193 AC-6 a failed run is logged and does not stop the next one")
  void aFailedRunDoesNotStopTheNextOne(CapturedOutput output) {
    var job = job(() -> buses(1, 0, 1), new InMemoryBusData(new IllegalStateException("database down")));

    assertThatCode(job::run).doesNotThrowAnyException();
    assertThat(MDC.get(Correlation.MDC_KEY)).isNull();
    assertThat(output.getAll()).contains("Transport source import failed").contains("database down");
  }

  @Test
  @DisplayName("193 AC-6 a successful read is logged at INFO, a failed one at WARN")
  void theLevelFollowsTheOutcome(CapturedOutput output) {
    job(() -> buses(4, 0, 1)).run();
    job(() -> SourceReading.invalid("not a JSON array", 1)).run();

    var lines = output.getAll().lines().filter(line -> line.contains("Transport source read")).toList();
    assertThat(lines).anySatisfy(line -> assertThat(line).contains("INFO").contains("Succeeded"));
    assertThat(lines).anySatisfy(line -> assertThat(line).contains("WARN").contains("Invalid"));
    assertThat(lines).noneSatisfy(line -> assertThat(line).contains("WARN").contains("Succeeded"));
  }

  @Test
  @DisplayName("193 AC-6 each run is logged with its outcome")
  void eachRunIsLogged(CapturedOutput output) {
    job(() -> buses(4, 0, 1)).run();
    job(() -> SourceReading.unavailable("HTTP 503", 3)).run();

    assertThat(output.getAll()).contains("Transport source read").contains("Succeeded").contains("Unavailable")
        .contains("HTTP 503");
  }

  @Test
  @DisplayName("US-40 AC-6 the run's log line has the late buses and the attempts")
  void logLineHasLateAndAttempts(CapturedOutput output) {
    job(() -> buses(3, 2, 2)).run();

    assertThat(output.getAll().lines().filter(line -> line.contains("Transport source read")))
        .anySatisfy(line -> assertThat(line).contains("vehicleCount=\"3\"").contains("late=\"2\"")
            .contains("attempts=\"2\""));
  }
}
